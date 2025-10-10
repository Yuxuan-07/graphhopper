# IFT3913 Tâche 2

## Noms

Peini Li & Yuxuan Yuan

## Classes sélectionnée

Les tests sont effectués sur la classe `NavigateResource`. Les tests unitaires ont été écrits dans le fichier `NavigateResourceTest.java`.

## 7 Tests ajoutés

1. ### **testDoGetMismatchBearings_NoReflection**

- **Intention:**

  Ce test vérifie que la méthode `doGet()` détecte bien une erreur quand le nombre de bearings ne correspond pas au nombre de points dans l’URL.
- **Données de test:**

  L’URL contient deux points (`/navigate/directions/v5/gh/car/1.0,2.0;3.0,4.0`), mais il n’y a qu’un seul bearing `("100,1")`. Tous les autres paramètres sont valides.
- **Oracle:**

  Le résultat attendu est une exception `IllegalArgumentException` avec un message contenant « Number of bearings ».

2. ### **testDoGetRejectRoundaboutExitsDisabled**

- **Intention:**

  Ce test sert à vérifier que `doGet()` rejette une requête quand le paramètre roundabout_exits est mis à false. Normalement, cette option doit toujours être activée pour bien gérer les rond-points.
- **Données de test:**

  L’URL utilisée (`/navigate/directions/v5/gh/car/1,1;2,2`) est valide et contient deux points.

  Tous les autres paramètres sont bons, sauf `roundaboutExits=false`, qui est volontairement désactivé pour ce test.
- **Oracle:**

  Le résultat attendu est une exception `IllegalArgumentException` contenant le mot « roundabout ».

  Si c’est bien le cas, ça prouve que la méthode empêche les requêtes qui ne respectent pas les règles attendues.

3. ### **testDoPostRejectLateHint_NoReflection**

- **Intention:**

  Ce test vérifie que `doPost()` refuse une requête contenant un paramètre (hint) interdit, comme `language`. L’objectif est de s’assurer que la méthode accepte seulement les paramètres autorisés par le protocole Mapbox.
- **Données de test:**

  On crée une requête GHRequest avec deux hints : `"type" = "mapbox"`(autorisé) et `"language" = "fr"` (interdit).
- **Oracle:**

  Le résultat attendu est une exception `IllegalArgumentException` dont le message contient « language » ou « illegal ». Cela montre que la validation interne détecte le paramètre interdit et bloque correctement la requête.

4. #### **testDoPostRejectWrongType**

- **Intention:**

  Ce test s’assure que `doPost()` rejette une requête quand le champ `"type"` est manquant ou invalide.

  Ce champ est obligatoire et doit toujours être `"mapbox"`.
- **Données de test:**

  On envoie simplement une requête vide (sans aucun hint).
- **Oracle:**

  Le résultat attendu est une exception `IllegalArgumentException` contenant le mot « type ».

  Ce résultat confirme que la méthode vérifie bien que le champ "type" est présent et correct avant de traiter la requête.

5. ### **testDoGetwithJavaFaker_NoBearings_2Points**

- **Intention:**

  Ce test vérifie que `doGet()` fonctionne normalement quand on lui envoie une requête simple avec deux points valides et aucun bearing. C’est un cas d’utilisation classique où on veut juste calculer un trajet direct entre deux endroits.
- **Données de test:**

  Les coordonnées des deux points sont générées avec JavaFaker, pour ressembler à des valeurs réalistes. Le paramètre bearings est vide, et tous les autres paramètres sont valides.
- **Oracle:**

  Le résultat attendu est un code `HTTP 200` (succès) et une réponse non vide.

  Le GHRequest utilisé par GraphHopper doit contenir deux points, et les options `CH.DISABLE` et `PASS_THROUGH` doivent être à `false`. Cela montre que la requête est bien traitée comme un trajet simple, sans configuration spéciale.

6. ### **Test 6: testDoGetwithJavaFaker_3Points**

- **Intention:**

  Ce test vérifie que `doGet()` gère correctement une requête avec trois points, c’est-à-dire un trajet avec une étape intermédiaire. Il faut s’assurer que le programme active les bonnes options pour les itinéraires à plusieurs segments.
- **Données de test:**

  On génère trois ensembles de coordonnées avec JavaFaker pour rendre la requête réaliste.

  Aucun paramètre bearings n’est fourni, et tous les autres paramètres sont valides.
- **Oracle:**

  Le résultat attendu est un code `HTTP 200` avec une réponse non vide.

  Le GHRequest doit contenir trois points, et les options `CH.DISABLE` et `PASS_THROUGH` doivent être à `true`. Cela prouve que la méthode configure bien le routage multi-étapes quand il y a plus de deux points.

7. ### **testDoPost**

- **Intention:**

  Vérifie que la méthode `doPost()` construit et exécute correctement une requête valide lorsque les composants internes de GraphHopper sont simulés. L’objectif est de s’assurer que la méthode fonctionne dans un scénario complet de succès, depuis la création du GHRequest jusqu’à l’appel à la méthode `route()`.
- **Données de test:**

  Les données de test incluent un GHRequest configuré avec les paramètres `type="mapbox"` et `profile="car"`.
- **Oracle:**

  Le résultat attendu est un code `HTTP 200`, indiquant que la requête s’est bien exécutée.

  Le test vérifie également que la méthode `route()` de GraphHopper a été appelée une seule fois et que l’objet GHRequest généré contient bien un PathDetail avec la clé `"time"`.

## Le score de mutation


|                   | Avant       | Après       |
| ----------------- | ----------- | ------------ |
| Line Coverage     | 10%(13/132) | 77%(102/132) |
| Mutation Coverage | 10%(5/49)   | 76%(37/49)   |
| Test Strength     | 100%(5/5)   | 84%(37/44)   |

## Mutants détectés

Les nouveaux tests détectent 32 nouveaux mutants. Il y a principalement quatre catégories: 

- **NEGATE_CONDITIONALS**

  Ce type de mutant inverse la condition d'un test logique.
  Cela change le comportement attendu du programme. 

  Dans `NavigateResource.java` :

  ```java
  if (!roundaboutExits)
              throw new IllegalArgumentException("Roundabout exits have to be enabled right now");
  ```

  Dans `NavigateResourceTest.java` :

  ```java
  public void testDoGetRejectRoundaboutExitsDisabled() {
   	 Exception exp1 = assertThrows(IllegalArgumentException.class, () -> nr.doGet(...,roundaboutExits:false,...)); assertTrue(exp1.getMessage().toLowerCase(Locale.ROOT).contains("roundabout"));
  }
  ```

  Dans cet exemple, si la condition était inversée (`if(roundaboutExits)`), aucune erreur ne serait levée quand `roundaboutExits=false`, et donc le mutant est tué.

  

- **EMPTY_RETURNS**

  Ce type de mutant remplace la valeur retournée par une collection vide. Cela change le comportement attendu du programme.

  Dans `NavigateResource.java` :

  ```java
  private List<GHPoint> getPointsFromRequest(HttpServletRequest httpServletRequest, String profile) {
        	...
          return points;
      }
  
  private GHResponse calcRouteForGET(){
    ...
    GHRequest request = new GHRequest(requestPoints);
    ...
  }
  
  public Response doGet(...){
    ...
    List<GHPoint> requestPoints = getPointsFromRequest(httpReq, mapboxProfile);
    GHResponse ghResponse = calcRouteForGET(favoredHeadings, requestPoints, ghProfile, localeStr, enableInstructions, minPathPrecision);
  
  }
  ```

  Dans `NavigateResourceTest.java` :

  ```java
  public void testDoGetwithJavaFaker_NoBearings_2Points() {
    ...
    ArgumentCaptor<GHRequest> captor = ArgumentCaptor.forClass(GHRequest.class);
    verify(gh).route(captor.capture());
    GHRequest request = captor.getValue();
    assertEquals(2, request.getPoints().size());
  }
  ```

  Dans cet exemple,  si la valeur retournée de `getPointsFromRequest` était remplacée par `Collections.emptyList()`, la requête GHRequest construite dans  `calcRouteForGet()` contiendrait zéro point (`request.getPoints().size() = 0`). Par conséquent, l'assertion échouerait et le mutant serait tué.

  

- **CONDITIONALS_BOUNDARY**
  Un mutant de type conditional boundary modifie une limite numérique dans une condition logique.
  Cela change le comportement attendu du programme.

  Dans `NavigateResource.java` :

  ```java
  if (requestPoints.size() > 2 || !headings.isEmpty()) {	
      request.putHint(Parameters.CH.DISABLE, true).	
          putHint(Parameters.Routing.PASS_THROUGH, true);
  }
  ```

  Dans `NavigateResourceTest.java` :

  ```java
  assertEquals(3, request.getPoints().size());
  assertTrue(request.getHints().getBool(Parameters.CH.DISABLE, false));
  assertTrue(request.getHints().getBool(Parameters.Routing.PASS_THROUGH, false));
  ```

  Ici, le code active certaines options uniquement quand il y a plus de 2 points.
  Si la condition devient >= 2, ces options s’activent dès qu'il y a 2 points, ce qui est incorrect.

  Les tests comme testDoGetwithJavaFaker_3Points vérifie le comportement à 2 et à 3 points :

    - à 2 points → les options ne doivent pas être activées,
    - à 3 points → elles doivent l’être. 

  Le test échoue si la condition est changée, donc il détecte ce mutant.

  
  
- **NULL_RETURNS**
  Un mutant de type null return remplace une valeur de retour normale par null.
  Cela simule les cas où la méthode ne renvoie rien, causant souvent des comportements innattendus.

  Dans `NavigateResource.java` :

  ```java
  DistanceConfig config = new DistanceConfig(unit, translationMap, request.getLocale(), graphHopper.getNavigationMode(request.getProfile()));		
      logger.info(logStr);	
      return Response.ok(NavigateResponseConverter.convertFromGHResponse(ghResponse, translationMap, request.getLocale(), config)).
          header("X-GH-Took", "" + Math.round(took * 1000)).	
          build();
  ```

  Dans `NavigateResourceTest.java` :

  ```java
  Response rep = nr.doPost(request, httpReq);
  assertEquals(200, rep.getStatus());
  assertNotNull(rep.getEntity());
  ```

  Ce code renvoie une réponse HTTP (objet Response) contenant les données du trajet.
  Si le mutant remplace cette ligne par return null;, le test échoue car Response devient null et les assertions suivantes ne peuvent plus être validées.

  Les tests comme testDoPost vérifient que la réponse n’est pas nulle et que le code HTTP correspond au comportement attendu, ce qui permet de détecter ce mutant.

## Utilisation de JavaFaker

Nous utilisons JavaFaker dans le teste 5 et 6 pour générer de manière aléatoire des coordonnées géographiques réalistes (latitude et longitude). Cela permet d'améliorer la diversité et l'authenticité des tests. Plus précisément, dans le test `testDoGetwithJavaFaker_3Points`, nous exécutons dix itérations afin de vérifier la robustesse de la méthode testée face à des entrées variées.

JavaFaker nous permet de simuler des requêtes plus proche des scénarios d'utilisation réels pendants les tests, garantissant que le code reste fiable même lorsqu’il est confronté à des données imprévisibles ou non déterministes.
