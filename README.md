# Ma Cuisine — launcher natif pour Monsieur Cuisine Connect (SPM188a / MT6580)

App Android native qui remplace le launcher Lidl (`de.silpion.mc2`) :
écran de cuisson (molettes → trames série réelles), catalogue de 225 recettes
avec photos, et cuisson guidée pas-à-pas.

## Build
Le push sur `main` déclenche GitHub Actions, qui compile l'APK et le publie
dans **Releases** (et dans les artifacts du run).

## Installer sur le robot
```
adb install -r MaCuisine-debug.apk
adb shell cmd package set-home-activity com.devbyeai.macuisine/.MainActivity
# revenir au launcher Lidl si besoin :
# adb shell cmd package set-home-activity de.silpion.mc2/<son activity>
```

## Pilotage matériel
- Port : `/dev/ttyMT0` @ 921600 (via `libserial_port.so`, réutilisée du stock).
- Protocole reverse dans `serial/PowerBoard.kt` (trame 15 octets, voir le doc).
- En V1 (APK debug, app normale), le port système n'est pas accessible :
  l'app tourne en **simulation**. Pour le pilotage réel, V2 = app système
  signée avec les clés plateforme (test-keys) + `sharedUserId=android.uid.system`.

⚠️ Les codes-commande du protocole restent à confirmer par capture série avant
de piloter la chauffe.

## Données
Ce dépôt ne contient PAS les recettes ni les photos de l'app d'origine
(contenu propriétaire Lidl/Vorwerk). Il embarque seulement un petit jeu de
recettes d'exemple, écrites pour le projet. Tes propres recettes/photos
restent chez toi et se chargent sur le robot séparément.
