# IFT3913 Tâche 2

## Noms

Peini Li & Yuxuan Yuan

## Classes sélectionnée

Les tests suivants portent sur la classe `SnapPreventionEdgeFilter` située dans `core/src/main/java/com/graphhopper/routing/util/SnapPreventionEdgeFilter.java` et sa classe de test `SnapPreventionEdgeFilterTest` dans `core/src/test/java/com/graphhopper/routing/util/SnapPreventionEdgeFilterTest.java`.

## Modification de workflows

### Conception de workflows :

L'objectif du workflow est de vérifier si le score de mutation baisse après un commit, afin de s'assurer que les changements n'abaissent pas la qualité des tests. Ainsi, nous devons : 

1. Conserver une valeur de référence afin de la comparer avec le score de mutation courant
2. Lancer une erreur lorsque le score de mutation est inférieur à la valuer de référence
3. Mettre à jour la baseline lorsque le score de mutation courant est supérieur à la référence 

### Implémentation pour cette modification :

Afin de permettre la lecture et la mise à jour de la valeur de référence entre différentes exécutions du CI, nous utilisons [action/cache](https://github.com/actions/cache), qui permet de stocker et de restaurer des fichiers générés lors d'un workflow.

1. `Build ${{ matrix.java-version }} & Run the tests` : 
   - Compile le projet et exécute les tests `SnapPreventionEdgeFilterTest` avec PITest
2. `Extraire le score de mutation` : 
   - Extrait le score de mutation depuis le rapport PITest `core/target/pit-reports/index.html`
   - Stocke cette valeur dans la variable d'environnement `MUTATION_SCORE` dans `GITHUB_ENV`  afin de la comparer avec la valeur de référence dans l'étape suivante du workflow
3. `Restaurer/Enregistrer le score de mutation de référence` : 
   - Restaure le score de référence depuis `mutation-score-baseline.txt` ou le enregistre
   - Pour éviter les conflits entre l'exécution des deux version de Java (24 et 25-ea), on introduit `${{ matrix.java-version }}` dans la clé du cache.
   - Comme la suavegarde se fait automatiquement dans la phase "post job", une seule étape suffit pour restaurer et enregistrer la valeur de référence.
4. `Comparer le score de mutation avec la valeur de référence` : 
   - Charge le valeur de référence dans la variable `$baseline`, puis la compare avec `$MUTATION_SCORE` : 
     - Si `MUTATION_SCORE ≥ baseline` : le workflow passe et on met à jour le score de référence dans l'étape suivante.
     - Si `MUTATION_SCORE < baseline`: le workflow échoue avec `exit 1`
5. `Mettre à jour le score de mutation de référence` : 
   - Écrire le score de mutation courant `$MUTATION_SCORE` dans `mutation-score-baseline.txt`.

### Manière de Validation :

1. Première exécution : Création du score de mutation de référence

   On exécute le workflow avec `SnapPreventionEdgeFilterTest` qui inclut les tests originaux et les tests ajoutés. Sur la page de Github Actions, on obtient :

   ```
   Score de mutation : 85
   Il n'existe aucune valeur de référence.
   Le nouveau score de mutation de référence : 85
   ```

2. Deuxième exécution : Simulation d'une régression

   Pour vérifier le détection de la baisse du score de mutation, on a créé un fichier `SnapPreventionEdgeFilterOriginalTest.java` contenant uniquement les tests originaux de la classe `SnapPreventionEdgeFilter`. On modifie temporairement le `-DtargetTests` de `SnapPreventionEdgeFilterTest`  à `SnapPreventionEdgeOriginalFilterTest`.  

   ```yaml
   - name: Build ${{ matrix.java-version }} & Run with original tests
     run: | 
       mvn -B -pl core -am test-compile \
         org.pitest:pitest-maven:1.20.4:mutationCoverage \
         -DtargetClasses='com.graphhopper.routing.util.SnapPreventionEdgeFilter' \
         -DtargetTests='com.graphhopper.routing.util.SnapPreventionEdgeOriginalFilterTest' \
         -DfailWhenNoMutations=false
   ```

   Comme les tests ajoutés ne sont pas exécutés, le score de mutation baisse. Sur la page de Github Actions, on obtient :

   ```
   Score de mutation : 60
   Score de mutation de référence : 85
   Error: Le score de mutation réduit de 85 à 60.
   Error: Process completed with exit code 1.
   ```

## 2 Tests ajoutés

### Test `constructorFailsForUnknownSnapPreventionValue`

1. **Choix des classes testées**  
   On teste `SnapPreventionEdgeFilter`, car c’est la classe qui lit directement la configuration `snap_prevention` et qui doit refuser immédiatement une valeur invalide dans son constructeur.

2. **Choix des classes simulées**  
   On simule `EdgeFilter`, `EnumEncodedValue<RoadClass>` et `EnumEncodedValue<RoadEnvironment>`, parce qu’on veut isoler le constructeur de `SnapPreventionEdgeFilter` sans dépendre d’un vrai graphe ni d’encoders réels.

3. **Définition des mocks**  
   On configure le `EdgeFilter` mocké pour qu’il renvoie toujours `true`, et on passe des encoders mockés au constructeur, pour être sûr que l’exception vient uniquement de la valeur dans `snapPreventions` et pas d’une autre dépendance.

4. **Choix des valeurs simulées**  
   On met la chaîne `"not_a_valid_snap_flag"` dans `snapPreventions` pour représenter une valeur totalement inconnue, et on vérifie que cela provoque une `IllegalArgumentException` avec `snap_prevention` dans le message.

---

### Test `snapPreventionFilter_usesMockedDependenciesToBlockConfiguredTypes`

1. **Choix des classes testées**  
    On teste `SnapPreventionEdgeFilter`, car c’est la classe qui décide d’accepter ou refuser une arête en fonction de la configuration `snap_prevention` et des valeurs lues, donc c’est le meilleur endroit pour vérifier ce comportement.

2. **Choix des classes simulées**  
   On simule `EdgeFilter`, `EnumEncodedValue<RoadClass>`, `EnumEncodedValue<RoadEnvironment>` et `EdgeIteratorState`, afin de contrôler exactement ce que renvoient le filtre de base et les lectures d’attributs sur l’arête sans créer de vrai graphe.

3. **Définition des mocks**  
   On fait en sorte que `EdgeFilter` renvoie toujours `true`, puis on programme l’arête mockée avec `when(edge.get(rcEnc/reEnc)).thenReturn(...)` pour lui faire jouer successivement une autoroute, une trunk, un tunnel et une route normale, tout en vérifiant que `baseFilter.accept(edge)` est bien appelé à chaque fois.

4. **Choix des valeurs simulées**  
   On configure `snapPreventions` avec les chaînes `"motorway"`, `"trunk"` et `"tunnel"`, puis on simule les couples `(MOTORWAY, ROAD)`, `(TRUNK, ROAD)`, `(RESIDENTIAL, TUNNEL)` et `(RESIDENTIAL, ROAD)` pour vérifier que le filtre refuse les types listés et accepte la route normale.

## Rickroll
Enfin, nous avons ajouté un job GitHub Actions `rickroll` dans le workflow `Build and Test` : il se déclenche uniquement quand le job de tests `build` échoue (tests qui ont un erreur ou score de mutation en baisse) et affiche dans le résumé de la run un message humoristique avec un lien vers une vidéo YouTube, ce qui satisfait la consigne de rickroll lorsque la suite de tests de GraphHopper échoue.