package com.tunai.desktop.viewer;

import javafx.animation.AnimationTimer;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SnapshotParameters;
import javafx.scene.SubScene;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.shape.VertexFormat;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;

/**
 * Visor 3D nativo de JavaFX (§2, §5.5). Cámara en órbita alrededor del modelo:
 * botón izquierdo gira, rueda hace zoom (con límites) y botón derecho desplaza.
 */
public class ModelViewer extends Pane {

    /** Vista inicial 3/4 frontal. */
    static final double INITIAL_YAW = -35;
    static final double INITIAL_PITCH = -18;
    static final double INITIAL_DISTANCE = 370;
    /** Más cerca atravesaría el modelo (radio ≈ TARGET_SIZE·√3/2). */
    static final double MIN_DISTANCE = GlbModel.TARGET_SIZE * 0.95;
    static final double MAX_DISTANCE = 1400;
    static final double AUTO_ROTATE_DEG_PER_SEC = 18;
    private static final Color BACKGROUND = Color.web("#eef1f5");

    private final Group modelGroup = new Group();
    private final PhongMaterial material = new PhongMaterial(Color.WHITE);
    private final PerspectiveCamera camera = new PerspectiveCamera(true);
    private final Rotate yaw = new Rotate(INITIAL_YAW, Rotate.Y_AXIS);
    private final Rotate pitch = new Rotate(INITIAL_PITCH, Rotate.X_AXIS);
    private final Translate pan = new Translate();
    private final Translate dolly = new Translate(0, 0, -INITIAL_DISTANCE);
    private final SubScene subScene;
    private final BooleanProperty autoRotate = new SimpleBooleanProperty(false);
    private final AnimationTimer autoRotateTimer;

    private double dragX;
    private double dragY;

    public ModelViewer() {
        material.setSpecularColor(Color.gray(0.18));
        material.setSpecularPower(24);

        camera.setNearClip(1);
        camera.setFieldOfView(35);
        camera.setFarClip(10_000);
        // La luz puntual viaja con la cámara: el lado visible siempre está iluminado.
        PointLight key = new PointLight(Color.gray(0.55));
        key.setTranslateY(-120);
        Group cameraRig = new Group(camera, key);
        cameraRig.getTransforms().addAll(pan, yaw, pitch, dolly);

        AmbientLight ambient = new AmbientLight(Color.gray(0.62));
        Group root = new Group(modelGroup, ambient, cameraRig);

        subScene = new SubScene(root, 100, 100, true, SceneAntialiasing.BALANCED);
        subScene.setFill(BACKGROUND);
        subScene.setCamera(camera);
        subScene.widthProperty().bind(widthProperty());
        subScene.heightProperty().bind(heightProperty());
        getChildren().add(subScene);
        setMinSize(0, 0);

        subScene.addEventHandler(MouseEvent.MOUSE_PRESSED, this::onPressed);
        subScene.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::onDragged);
        subScene.addEventHandler(ScrollEvent.SCROLL, this::onScroll);

        autoRotateTimer = new AnimationTimer() {
            private long last;

            @Override
            public void start() {
                last = 0;
                super.start();
            }

            @Override
            public void handle(long now) {
                if (last > 0) {
                    yaw.setAngle(yaw.getAngle() + AUTO_ROTATE_DEG_PER_SEC * (now - last) / 1e9);
                }
                last = now;
            }
        };
        autoRotate.addListener((obs, was, on) -> {
            if (on) {
                autoRotateTimer.start();
            } else {
                autoRotateTimer.stop();
            }
        });
    }

    /** Muestra el modelo con la textura indicada. Debe llamarse en el hilo de JavaFX. */
    public void setModel(GlbModel model, Image texture) {
        TriangleMesh mesh = new TriangleMesh(VertexFormat.POINT_NORMAL_TEXCOORD);
        mesh.getPoints().setAll(model.points());
        mesh.getNormals().setAll(model.normals());
        mesh.getTexCoords().setAll(model.texCoords());
        // En glTF el índice de posición, normal y UV es el mismo: p, n, t se repiten (§3, punto 2).
        int[] indices = model.indices();
        int[] faces = new int[indices.length * 3];
        for (int i = 0; i < indices.length; i++) {
            faces[i * 3] = indices[i];
            faces[i * 3 + 1] = indices[i];
            faces[i * 3 + 2] = indices[i];
        }
        mesh.getFaces().setAll(faces);

        MeshView view = new MeshView(mesh);
        view.setMaterial(material);
        view.setCullFace(CullFace.BACK);
        view.setDrawMode(DrawMode.FILL);
        modelGroup.getChildren().setAll(view);
        setTexture(texture);
        resetView();
    }

    public void setTexture(Image texture) {
        material.setDiffuseMap(texture);
    }

    public void resetView() {
        yaw.setAngle(INITIAL_YAW);
        pitch.setAngle(INITIAL_PITCH);
        dolly.setZ(-INITIAL_DISTANCE);
        pan.setX(0);
        pan.setY(0);
        pan.setZ(0);
    }

    public BooleanProperty autoRotateProperty() {
        return autoRotate;
    }

    public void dispose() {
        autoRotate.unbind();
        autoRotate.set(false);
        autoRotateTimer.stop();
    }

    /** Captura del visor (§5.5, exportar PNG y miniatura del garaje). */
    public WritableImage snapshot() {
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(BACKGROUND);
        return subScene.snapshot(params, null);
    }

    double distance() {
        return -dolly.getZ();
    }

    double yawAngle() {
        return yaw.getAngle();
    }

    private void onPressed(MouseEvent e) {
        dragX = e.getSceneX();
        dragY = e.getSceneY();
        subScene.requestFocus();
    }

    private void onDragged(MouseEvent e) {
        double dx = e.getSceneX() - dragX;
        double dy = e.getSceneY() - dragY;
        dragX = e.getSceneX();
        dragY = e.getSceneY();
        if (e.getButton() == MouseButton.PRIMARY) {
            yaw.setAngle(yaw.getAngle() + dx * 0.4);
            pitch.setAngle(clamp(pitch.getAngle() - dy * 0.4, -85, 85));
        } else if (e.getButton() == MouseButton.SECONDARY) {
            // Desplaza en el plano de la pantalla, proporcional a la distancia.
            double factor = distance() / 900;
            double yawRad = Math.toRadians(yaw.getAngle());
            pan.setX(pan.getX() - dx * factor * Math.cos(yawRad));
            pan.setZ(pan.getZ() + dx * factor * Math.sin(yawRad));
            pan.setY(clamp(pan.getY() - dy * factor, -GlbModel.TARGET_SIZE, GlbModel.TARGET_SIZE));
        }
    }

    private void onScroll(ScrollEvent e) {
        zoom(e.getDeltaY());
    }

    void zoom(double delta) {
        double factor = Math.pow(1.0015, -delta);
        dolly.setZ(-clamp(distance() * factor, MIN_DISTANCE, MAX_DISTANCE));
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
