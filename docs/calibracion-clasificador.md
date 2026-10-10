# Calibración del clasificador de vehículos

**Fotos usadas:** 30 imágenes de Wikimedia Commons (licencias libres), descargadas por búsqueda: 15 de carros comunes en Colombia en distintos ángulos y colores, y 15 que no son carros (personas, animales, objetos, una moto, interiores de carro). No se incluyen en el repositorio.

**Decisión:** umbral **0,45** (el inicial era 0,40). Con 0,40 tampoco hubo falsos positivos, pero el interior de carro con la puerta abierta sacó 0,377, muy cerca. Con 0,45 el margen es de 0,073 y los falsos negativos no cambian (el único es un Chevrolet Onix con un vinilo muy cargado).

**Pendiente:** repetir con fotos reales tomadas por el equipo (sobre todo del carro de la demo) antes del ensayo. Para repetirla:

```
mvnw test-compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt
java -cp "target/classes;target/test-classes;<contenido de target/cp.txt>" com.tunai.desktop.classifier.CalibrationTool <carpeta con vehiculos/ y otros/> docs/calibracion-clasificador.md 0.45
```

El umbral se cambia sin recompilar en `%APPDATA%\TunAI\settings.json` → `"classifier": {"threshold": 0.45}`.

Generado por `CalibrationTool` el 2026-10-10. Modelo: MobileNetV2 (ONNX Model Zoo, `mobilenetv2-12.onnx`).

- Umbral probado: **0.45**
- Fotos: 15 de vehículos, 15 de otras cosas
- Falsos positivos (no carro aceptado): **0**
- Falsos negativos (carro rechazado): **1** de 15
- Suma más alta entre las fotos que no son carros: 0.377
- Tiempo medio por foto (sin contar la primera): 12 ms

| Grupo | Foto | Top 5 | Suma vehículo | Decisión | Resultado |
|---|---|---|---|---|---|
| vehiculos | 01-Mazda-3-sedan-front-three-quarter.jpg | car wheel 20.0%<br>🚗 beach wagon 19.4%<br>🚗 minivan 16.0%<br>🚗 sports car 13.6%<br>parking meter 7.6% | 0.489 | vehículo | ✓ |
| vehiculos | 02-Renault-Twingo.jpg | 🚗 minivan 57.4%<br>🚗 beach wagon 7.4%<br>greenhouse 3.8%<br>thatch 2.1%<br>🚗 cab 2.0% | 0.669 | vehículo | ✓ |
| vehiculos | 03-Chevrolet-Spark.jpg | 🚗 minivan 82.0%<br>🚗 beach wagon 3.0%<br>car wheel 2.6%<br>grille 1.8%<br>🚗 convertible 1.1% | 0.862 | vehículo | ✓ |
| vehiculos | 04-Toyota-Corolla-2020-front.jpg | 🚗 minivan 27.9%<br>🚗 beach wagon 17.7%<br>grille 16.3%<br>🚗 pickup 10.1%<br>🚗 limousine 8.5% | 0.642 | vehículo | ✓ |
| vehiculos | 05-Kia-Picanto.jpg | 🚗 minivan 72.7%<br>🚗 beach wagon 11.7%<br>car wheel 5.4%<br>grille 2.4%<br>🚗 sports car 2.1% | 0.866 | vehículo | ✓ |
| vehiculos | 06-Volkswagen-Golf-rear-three-quarter.jpg | 🚗 minivan 35.9%<br>🚗 pickup 32.7%<br>🚗 beach wagon 9.0%<br>🚗 cab 6.1%<br>🚗 minibus 2.7% | 0.864 | vehículo | ✓ |
| vehiculos | 07-Ford-Mustang-convertible.jpg | 🚗 sports car 68.0%<br>🚗 convertible 28.7%<br>🚗 racer 1.4%<br>car wheel 1.0%<br>grille 0.4% | 0.981 | vehículo | ✓ |
| vehiculos | 08-Honda-Civic-hatchback-side.jpg | forklift 39.5%<br>🚗 tow truck 27.2%<br>🚗 racer 14.6%<br>car wheel 7.2%<br>🚗 sports car 4.4% | 0.462 | vehículo | ✓ |
| vehiculos | 09-Suzuki-Swift-red.jpg | 🚗 sports car 31.8%<br>🚗 convertible 25.3%<br>🚗 minivan 12.0%<br>🚗 beach wagon 10.7%<br>🚗 pickup 4.2% | 0.839 | vehículo | ✓ |
| vehiculos | 10-Toyota-Hilux-pickup.jpg | 🚗 sports car 22.3%<br>grille 15.2%<br>🚗 minivan 10.6%<br>🚗 convertible 8.6%<br>🚗 beach wagon 7.0% | 0.484 | vehículo | ✓ |
| vehiculos | 11-Nissan-Versa.jpg | 🚗 minivan 48.5%<br>grille 18.4%<br>🚗 sports car 14.2%<br>🚗 convertible 5.1%<br>car wheel 4.9% | 0.679 | vehículo | ✓ |
| vehiculos | 12-BMW-3-series-side-view.jpg | 🚗 beach wagon 54.7%<br>🚗 convertible 21.5%<br>🚗 sports car 10.6%<br>car wheel 6.4%<br>🚗 racer 2.6% | 0.895 | vehículo | ✓ |
| vehiculos | 13-Chevrolet-Onix.jpg | 🚗 sports car 20.4%<br>plastic bag 14.2%<br>🚗 racer 11.7%<br>🚗 cab 6.1%<br>umbrella 5.4% | 0.381 | no vehículo | ✗ falso negativo |
| vehiculos | 14-Renault-Duster.jpg | 🚗 pickup 25.0%<br>🚗 beach wagon 17.1%<br>🚗 jeep 10.1%<br>🚗 minivan 8.3%<br>car wheel 6.2% | 0.605 | vehículo | ✓ |
| vehiculos | 15-Hyundai-Tucson-SUV.jpg | 🚗 minivan 37.2%<br>🚗 beach wagon 30.0%<br>grille 8.9%<br>car wheel 6.5%<br>🚗 limousine 3.4% | 0.706 | vehículo | ✓ |
| otros | 01-golden-retriever-dog-portrait.jpg | Great Pyrenees 38.7%<br>kuvasz 36.5%<br>golden retriever 15.0%<br>Labrador retriever 5.7%<br>clumber 1.7% | 0.000 | no vehículo | ✓ |
| otros | 02-tabby-cat-sitting.jpg | wallaby 88.6%<br>cougar 3.6%<br>mongoose 2.0%<br>Siamese cat 1.6%<br>ibex 0.5% | 0.000 | no vehículo | ✓ |
| otros | 03-woman-portrait-smiling.jpg | Airedale 30.3%<br>Chesapeake Bay retriever 14.7%<br>Rottweiler 7.5%<br>bluetick 6.4%<br>Border terrier 3.6% | 0.000 | no vehículo | ✓ |
| otros | 04-man-walking-street.jpg | prison 43.5%<br>streetcar 9.0%<br>jinrikisha 6.5%<br>palace 6.0%<br>gondola 4.3% | 0.000 | no vehículo | ✓ |
| otros | 05-coffee-mug-on-table.jpg | coffee mug 58.1%<br>cup 36.9%<br>espresso 1.9%<br>coffeepot 1.1%<br>pitcher 0.8% | 0.000 | no vehículo | ✓ |
| otros | 06-Yamaha-motorcycle-side.jpg | lawn mower 33.8%<br>🚗 Model T 11.1%<br>harvester 9.8%<br>motor scooter 5.7%<br>shopping basket 5.1% | 0.111 | no vehículo | ✓ |
| otros | 07-car-interior-dashboard.jpg | 🚗 jeep 16.2%<br>🚗 tow truck 8.3%<br>🚗 minibus 4.9%<br>🚗 moving van 4.2%<br>🚗 minivan 4.1% | 0.377 | no vehículo | ✓ |
| otros | 08-bicycle-parked.jpg | swab 25.6%<br>ski 9.7%<br>shovel 8.6%<br>broom 8.1%<br>folding chair 3.6% | 0.000 | no vehículo | ✓ |
| otros | 09-laptop-computer-desk.jpg | desk 36.6%<br>radio 18.9%<br>desktop computer 12.3%<br>printer 5.0%<br>notebook 5.0% | 0.000 | no vehículo | ✓ |
| otros | 10-house-facade.jpg | liner 49.1%<br>umbrella 11.7%<br>shopping cart 5.0%<br>maze 4.1%<br>dome 2.9% | 0.000 | no vehículo | ✓ |
| otros | 11-horse-in-field.jpg | ram 36.0%<br>plow 28.6%<br>Arabian camel 11.4%<br>hartebeest 6.0%<br>bighorn 5.1% | 0.000 | no vehículo | ✓ |
| otros | 12-potted-plant.jpg | armadillo 8.2%<br>walking stick 7.0%<br>rain barrel 4.8%<br>water snake 4.6%<br>wine bottle 3.5% | 0.000 | no vehículo | ✓ |
| otros | 13-steering-wheel-car-interior.jpg | disk brake 36.1%<br>oxygen mask 33.7%<br>gasmask 8.3%<br>oil filter 7.2%<br>car mirror 2.3% | 0.000 | no vehículo | ✓ |
| otros | 14-child-playing-park.jpg | megalith 18.4%<br>worm fence 11.0%<br>hay 9.8%<br>barn 6.9%<br>golf ball 5.4% | 0.000 | no vehículo | ✓ |
| otros | 15-sofa-living-room.jpg | studio couch 63.0%<br>home theater 24.6%<br>pillow 3.6%<br>window shade 3.3%<br>cinema 2.4% | 0.000 | no vehículo | ✓ |
