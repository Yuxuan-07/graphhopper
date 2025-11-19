package com.graphhopper.routing.util;

import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.routing.ev.RoadEnvironment;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;
import com.graphhopper.util.Parameters;

public class SnapPreventionEdgeFilterTest {

    @Test
    public void accept() {
        EdgeFilter trueFilter = edgeState -> true;
        EncodingManager em = new EncodingManager.Builder().add(RoadClass.create()).add(RoadEnvironment.create()).build();
        EnumEncodedValue<RoadClass> rcEnc = em.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        EnumEncodedValue<RoadEnvironment> reEnc = em.getEnumEncodedValue(RoadEnvironment.KEY, RoadEnvironment.class);
        SnapPreventionEdgeFilter filter = new SnapPreventionEdgeFilter(trueFilter, rcEnc, reEnc, Arrays.asList("motorway", "ferry"));
        BaseGraph graph = new BaseGraph.Builder(em).create();
        EdgeIteratorState edge = graph.edge(0, 1).setDistance(1);

        assertTrue(filter.accept(edge));
        edge.set(reEnc, RoadEnvironment.FERRY);
        assertFalse(filter.accept(edge));
        edge.set(reEnc, RoadEnvironment.FORD);
        assertTrue(filter.accept(edge));

        edge.set(rcEnc, RoadClass.RESIDENTIAL);
        assertTrue(filter.accept(edge));
        edge.set(rcEnc, RoadClass.MOTORWAY);
        assertFalse(filter.accept(edge));
    }

    @Test
    public void constructorFailsForUnknownSnapPreventionValue() {
        EdgeFilter delegate = mock(EdgeFilter.class);
        when(delegate.accept(any())).thenReturn(true);

        @SuppressWarnings("unchecked")
        EnumEncodedValue<RoadClass> rcEnc = mock(EnumEncodedValue.class);
        @SuppressWarnings("unchecked")
        EnumEncodedValue<RoadEnvironment> reEnc = mock(EnumEncodedValue.class);

        List<String> bogusConfig = List.of("not_a_valid_snap_flag");

        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> new SnapPreventionEdgeFilter(delegate, rcEnc, reEnc, bogusConfig));

        String message = thrown.getMessage().toLowerCase(java.util.Locale.ROOT);
        assertTrue(message.contains(Parameters.Routing.SNAP_PREVENTION));
    }

    @Test
    public void snapPreventionFilter_usesMockedDependenciesToBlockConfiguredTypes() {
        EdgeFilter baseFilter = mock(EdgeFilter.class);

        @SuppressWarnings("unchecked")
        EnumEncodedValue<RoadClass> rcEnc = mock(EnumEncodedValue.class);

        @SuppressWarnings("unchecked")
        EnumEncodedValue<RoadEnvironment> reEnc = mock(EnumEncodedValue.class);

        EdgeIteratorState edge = mock(EdgeIteratorState.class);

        when(baseFilter.accept(edge)).thenReturn(true);

        SnapPreventionEdgeFilter filter = new SnapPreventionEdgeFilter(
                baseFilter,
                rcEnc,
                reEnc,
                List.of("motorway", "trunk", "tunnel"));

        when(edge.get(rcEnc)).thenReturn(RoadClass.MOTORWAY);
        when(edge.get(reEnc)).thenReturn(RoadEnvironment.ROAD);
        assertFalse(filter.accept(edge), "Une motorway doit être refusée");

        when(edge.get(rcEnc)).thenReturn(RoadClass.TRUNK);
        when(edge.get(reEnc)).thenReturn(RoadEnvironment.ROAD);
        assertFalse(filter.accept(edge), "Une trunk doit être refusée");

        when(edge.get(rcEnc)).thenReturn(RoadClass.RESIDENTIAL);
        when(edge.get(reEnc)).thenReturn(RoadEnvironment.TUNNEL);
        assertFalse(filter.accept(edge), "Un tunnel doit être refusé");

        when(edge.get(rcEnc)).thenReturn(RoadClass.RESIDENTIAL);
        when(edge.get(reEnc)).thenReturn(RoadEnvironment.ROAD);
        assertTrue(filter.accept(edge), "Une route normale doit être acceptée");

        verify(baseFilter, times(4)).accept(edge);
    }
}
