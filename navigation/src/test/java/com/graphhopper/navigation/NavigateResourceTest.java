package com.graphhopper.navigation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;
import com.github.javafaker.Faker;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.routing.ev.MaxSpeed;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.TranslationMap;
import com.graphhopper.ResponsePath;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.core.Response;

public class NavigateResourceTest {

    private NavigateResource makeResource() {
        GraphHopper hopper = mock(GraphHopper.class);
        TranslationMap translationMap = new TranslationMap();
        GraphHopperConfig config = new GraphHopperConfig();
        return new NavigateResource(hopper, translationMap, config);
    }

    private HttpServletRequest mockHttpReq(String uri) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn(uri);
        when(req.getRemoteAddr()).thenReturn("127.0.0.1");
        when(req.getLocale()).thenReturn(Locale.ENGLISH);
        when(req.getHeader(anyString())).thenReturn("JUnit");
        when(req.getQueryString()).thenReturn("");
        return req;
    }

    /**
     * Test original, à ne pas toucher
     */
    @Test
    public void voiceInstructionsTest() {

        List<Double> bearings = NavigateResource.getBearing("");
        assertEquals(0, bearings.size());
        assertEquals(Collections.EMPTY_LIST, bearings);

        bearings = NavigateResource.getBearing("100,1");
        assertEquals(1, bearings.size());
        assertEquals(100, bearings.get(0), .1);

        bearings = NavigateResource.getBearing(";100,1;;");
        assertEquals(4, bearings.size());
        assertEquals(100, bearings.get(1), .1);
    }


    /**
     * Vérifie que doGet() lance une IllegalArgumentException
     * lorsque le nombre de bearings ne correspond pas au nombre de points.
     * 
     * Données : 2 points dans l’URL, 1 seul bearing ("100,1").
     * Résultat attendu : message contenant "Number of bearings".
     */
    @Test
    public void testDoGetMismatchBearings_NoReflection() {
        // Préparation du mock
        GraphHopper gh = mock(GraphHopper.class);
        when(gh.route(any())).thenReturn(new GHResponse()); 

        TranslationMap translationMap = new TranslationMap();
        GraphHopperConfig config = new GraphHopperConfig();
        NavigateResource nr = new NavigateResource(gh, translationMap, config);

        // Mock du HttpServletRequest avec 2 points
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/navigate/directions/v5/gh/car/1.0,2.0;3.0,4.0");

        // Exécution : tout est valide sauf le nombre de bearings
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> nr.doGet(
                req,
                mock(UriInfo.class),
                mock(ContainerRequestContext.class),
                true,   // enableInstructions
                true,   // voiceInstructions
                true,   // bannerInstructions
                true,   // roundaboutExits
                "metric",
                "simplified",
                "polyline6",
                "100,1", // un seul bearing
                "en",
                "car"
        ));

        assertTrue(ex.getMessage().contains("Number of bearings"));
    }
    
    /**
     * Vérifie que doGet() lance une IllegalArgumentException
     * lorsque roundabout_exits est désactivé (false).
     * 
     * Données : 2 points valides dans l’URL, roundaboutExits=false.
     * Résultat attendu : message contenant "roundabout".
     */
    @Test
    public void testDoGetRejectRoundaboutExitsDisabled() {
        NavigateResource nr = makeResource();
        HttpServletRequest req = mockHttpReq("/navigate/directions/v5/gh/car/1,1;2,2");

        Exception exp1 = assertThrows(IllegalArgumentException.class, () -> nr.doGet(
                req, mock(UriInfo.class), mock(ContainerRequestContext.class),
                true, true, true, false,
                "metric", "simplified", "polyline6",
                "", "en", "car"));
        assertTrue(exp1.getMessage().toLowerCase(Locale.ROOT).contains("roundabout"));
    }

    /**
     * Vérifie que doPost() rejette une requête contenant un hint interdit.
     * 
     * Données : GHRequest avec type="mapbox" et language="fr" (non autorisé).
     * Résultat attendu : IllegalArgumentException avec message "language" ou "illegal".
     */
    @Test
    public void testDoPostRejectLateHint_NoReflection() {
        GraphHopper gh = mock(GraphHopper.class);
        when(gh.route(any())).thenReturn(new GHResponse());

        TranslationMap tm = new TranslationMap();
        GraphHopperConfig cfg = new GraphHopperConfig();
        NavigateResource nr = new NavigateResource(gh, tm, cfg);

        // Requête avec un hint interdit
        GHRequest request = new GHRequest();
        request.putHint("type", "mapbox");
        request.putHint("language", "fr"); // interdit

        HttpServletRequest httpReq = mock(HttpServletRequest.class);
        when(httpReq.getLocale()).thenReturn(Locale.CANADA_FRENCH);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> nr.doPost(request, httpReq));

        String msg = ex.getMessage().toLowerCase(Locale.ROOT);
        assertTrue(msg.contains("language") || msg.contains("illegal"),
                "Message inattendu : " + msg);
    }

    /**
     * Vérifie que doPost() rejette une requête sans type=mapbox.
     * 
     * Données : GHRequest sans hint "type".
     * Résultat attendu : IllegalArgumentException contenant "type".
     */
    @Test
    public void testDoPostRejectWrongType() {
        NavigateResource nr = makeResource();
        GHRequest request = new GHRequest(); // pas de hint "type=mapbox"
        HttpServletRequest req = mock(HttpServletRequest.class);

        Exception exp = assertThrows(IllegalArgumentException.class, () -> nr.doPost(request, req));
        assertTrue(exp.getMessage().contains("type"));
    }

    /**
     * Vérifie les méthodes privées calcRouteForGET() et getPointsFromRequest() 
     * fonctionnent avec 2 points valides sans bearings.
     * 
     * Données : 2 points générés avec JavaFaker, bearings vide.
     * Résultat attendu : code HTTP 200, 2 points dans la requête,
     * CH.DISABLE=false et PASS_THROUGH=false.
     */
    @Test
    public void testDoGetwithJavaFaker_NoBearings_2Points() {
        GraphHopper gh = mock(GraphHopper.class);
        GHResponse gp = mock(GHResponse.class, RETURNS_DEEP_STUBS); 
        when(gh.getNavigationMode("car")).thenReturn(TransportationMode.CAR);
        when(gh.route(any())).thenReturn(gp);

        TranslationMap translationMap = new TranslationMap();
        GraphHopperConfig config = new GraphHopperConfig();
        NavigateResource nr = new NavigateResource(gh, translationMap, config);

        HttpServletRequest req = mock(HttpServletRequest.class);
        Faker faker = new Faker(Locale.ENGLISH);
        String point1 = faker.address().longitude() + ',' + faker.address().latitude();
        String point2 = faker.address().longitude() + ',' + faker.address().latitude();
        String url = "/navigate/directions/v5/gh/car/" + point1 + ';' + point2;

        when(req.getRequestURI()).thenReturn(url);

        Response rep = nr.doGet(
                req,
                mock(UriInfo.class),
                mock(ContainerRequestContext.class),
                true,   // enableInstructions
                true,   // voiceInstructions
                true,   // bannerInstructions
                true,   // roundaboutExits
                "metric",
                "simplified",
                "polyline6",
                "",
                "en",
                "car"
        );

        assertEquals(200, rep.getStatus());
        assertNotNull(rep.getEntity());
        ArgumentCaptor<GHRequest> captor = ArgumentCaptor.forClass(GHRequest.class);
        verify(gh).route(captor.capture());
        GHRequest request = captor.getValue();
        assertEquals(2, request.getPoints().size());
        assertFalse(request.getHints().getBool(Parameters.CH.DISABLE, false));
        assertFalse(request.getHints().getBool(Parameters.Routing.PASS_THROUGH, false));
        assertEquals(1.0,request.getHints().getDouble(Parameters.Routing.WAY_POINT_MAX_DISTANCE, 0));
    }

    /**
     * Vérifie les méthodes privées calcRouteForGET() et getPointsFromRequest() 
     * fonctionnent avec 3 points générés aléatoirement avec JavaFaker
     * 
     * Données : 3 points générés avec JavaFaker, bearings vide.
     * Résultat attendu : code HTTP 200, 3 points dans la requête,
     * CH.DISABLE=true et PASS_THROUGH=true.
     */
    @Test
    public void testDoGetwithJavaFaker_3Points() {
        GraphHopper gh = mock(GraphHopper.class);
        GHResponse gp = mock(GHResponse.class, RETURNS_DEEP_STUBS); 
        when(gh.getNavigationMode("car")).thenReturn(TransportationMode.CAR);
        when(gh.route(any())).thenReturn(gp); 

        TranslationMap translationMap = new TranslationMap();
        GraphHopperConfig config = new GraphHopperConfig();
        NavigateResource nr = new NavigateResource(gh, translationMap, config);

        HttpServletRequest req = mock(HttpServletRequest.class);
        
        for (int i = 0; i < 10; i++){
            Faker faker = new Faker(Locale.ENGLISH);
            String point1 = faker.address().longitude() + ',' + faker.address().latitude();
            String point2 = faker.address().longitude() + ',' + faker.address().latitude();
            String point3 = faker.address().longitude() + ',' + faker.address().latitude();
            String url = "/navigate/directions/v5/gh/car/" + point1 + ';' + point2 + ';' + point3;
            when(req.getRequestURI()).thenReturn(url);
            Response rep = nr.doGet(
                req,
                mock(UriInfo.class),
                mock(ContainerRequestContext.class),
                true,   // enableInstructions
                true,   // voiceInstructions
                true,   // bannerInstructions
                true,   // roundaboutExits
                "metric",
                "simplified",
                "polyline6",
                "",
                "en",
                "car"
            );

            assertEquals(200, rep.getStatus());
            assertNotNull(rep.getEntity());
            ArgumentCaptor<GHRequest> captor = ArgumentCaptor.forClass(GHRequest.class);
            verify(gh).route(captor.capture());
            GHRequest request = captor.getValue();
            assertEquals(3, request.getPoints().size());
            assertTrue(request.getHints().getBool(Parameters.CH.DISABLE, false));
            assertTrue(request.getHints().getBool(Parameters.Routing.PASS_THROUGH, false));
            clearInvocations(gh);
        }
    }
    
    /**
     * Vérifie que doPost() construit et exécute correctement une requête valide.
     * 
     * Données : type="mapbox", profile="car", avec EncodingManager et MaxSpeed.KEY simulés.
     * Résultat attendu : code HTTP 200, route() appelé une fois,
     * et GHRequest contenant un PathDetail "time".
     */
    @Test
    public void testDoPost() {
        GraphHopper gh = mock(GraphHopper.class);
        EncodingManager em = mock(EncodingManager.class);
        GHResponse gp = mock(GHResponse.class, RETURNS_DEEP_STUBS);
        gp.add(mock(ResponsePath.class,RETURNS_DEEP_STUBS));
        when(gh.getEncodingManager()).thenReturn(em);
        when(em.hasEncodedValue(MaxSpeed.KEY)).thenReturn(true);
        when(gh.getNavigationMode(anyString())).thenReturn(TransportationMode.CAR);
        when(gh.route(any())).thenReturn(gp);


        TranslationMap tm = new TranslationMap();
        GraphHopperConfig cfg = new GraphHopperConfig();
        NavigateResource nr = new NavigateResource(gh, tm, cfg);

        GHRequest request = new GHRequest();
        request.putHint("type", "mapbox");
        request.putHint("profile", "car");

        HttpServletRequest httpReq = mock(HttpServletRequest.class);

        Response rep = nr.doPost(request, httpReq);
        assertEquals(200, rep.getStatus());

        ArgumentCaptor<GHRequest> captor = ArgumentCaptor.forClass(GHRequest.class);
        verify(gh).route(captor.capture());
        assertNotNull(captor.getValue().getPathDetails());
        assertTrue(captor.getValue().getPathDetails().contains("time"));
    }
}
