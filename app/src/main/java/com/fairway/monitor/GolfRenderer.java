package com.fairway.monitor;

import android.content.Context;
import android.opengl.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Offline OpenGL ES 2 scene with actual CC0 Kenney meshes and procedural range architecture. */
public final class GolfRenderer implements GLSurfaceView.Renderer {
  private final Context context;
  public final GolfGame game;
  private final Map<String, Mesh> models = new HashMap<>();
  private Mesh cube, disc, cone;
  private int program,
      position,
      normal,
      color,
      mvpLocation,
      modelLocation,
      tintLocation,
      eyeLocation;
  private final float[] projection = new float[16],
      view = new float[16],
      model = new float[16],
      vp = new float[16],
      mvp = new float[16];
  private long previous;
  private float aspect = 1;
  public volatile String error;

  public GolfRenderer(Context context, GolfGame game) {
    this.context = context;
    this.game = game;
  }

  @Override
  public void onSurfaceCreated(GL10 gl, EGLConfig config) {
    try {
      String vs =
          "attribute vec3 aPosition;attribute vec3 aNormal;attribute vec3 aColor;uniform mat4"
              + " uMvp;uniform mat4 uModel;uniform vec3 uTint;uniform vec3 uEye;varying vec3"
              + " vColor;varying float vFog;void main(){vec3"
              + " n=normalize(mat3(uModel)*aNormal);float"
              + " light=.48+.52*max(dot(n,normalize(vec3(-.4,1.,-.3))),0.);vColor=aColor*uTint*light;vec3"
              + " world=(uModel*vec4(aPosition,1.)).xyz;vFog=clamp(length(world-uEye)/420.,0.,.75);gl_Position=uMvp*vec4(aPosition,1.);}";
      String fs =
          "precision mediump float;varying vec3 vColor;varying float vFog;void"
              + " main(){gl_FragColor=vec4(mix(vColor,vec3(.67,.81,.83),vFog),1.);}";
      program = GLES20.glCreateProgram();
      GLES20.glAttachShader(program, shader(GLES20.GL_VERTEX_SHADER, vs));
      GLES20.glAttachShader(program, shader(GLES20.GL_FRAGMENT_SHADER, fs));
      GLES20.glLinkProgram(program);
      int[] ok = new int[1];
      GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, ok, 0);
      if (ok[0] == 0) throw new IOException(GLES20.glGetProgramInfoLog(program));
      position = GLES20.glGetAttribLocation(program, "aPosition");
      normal = GLES20.glGetAttribLocation(program, "aNormal");
      color = GLES20.glGetAttribLocation(program, "aColor");
      mvpLocation = GLES20.glGetUniformLocation(program, "uMvp");
      modelLocation = GLES20.glGetUniformLocation(program, "uModel");
      tintLocation = GLES20.glGetUniformLocation(program, "uTint");
      eyeLocation = GLES20.glGetUniformLocation(program, "uEye");
      cube = makeCube();
      disc = makeRound(false);
      cone = makeRound(true);
      for (String name :
          new String[] {
            "ball-blue",
            "flag-red",
            "flag-blue",
            "flag-green",
            "castle",
            "structure-windmill",
            "obstacle-block",
            "structure-gate",
            "open"
          }) models.put(name, load(name));
      GLES20.glEnable(GLES20.GL_DEPTH_TEST);
      GLES20.glDisable(GLES20.GL_CULL_FACE);
      previous = System.nanoTime();
    } catch (Exception e) {
      error = "3D renderer could not load: " + e.getMessage();
    }
  }

  private int shader(int type, String code) throws IOException {
    int shader = GLES20.glCreateShader(type);
    GLES20.glShaderSource(shader, code);
    GLES20.glCompileShader(shader);
    int[] ok = new int[1];
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0);
    if (ok[0] == 0) throw new IOException(GLES20.glGetShaderInfoLog(shader));
    return shader;
  }

  @Override
  public void onSurfaceChanged(GL10 gl, int w, int h) {
    // Reserve the top and bottom for the native controls.
    int bottom = Math.round(h * .31f), height = Math.round(h * .50f);
    GLES20.glViewport(0, bottom, w, height);
    aspect = w / (float) Math.max(1, height);
    Matrix.perspectiveM(projection, 0, 48, aspect, .1f, 650);
  }

  @Override
  public void onDrawFrame(GL10 gl) {
    GLES20.glClearColor(.67f, .81f, .83f, 1);
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
    if (error != null) return;
    long now = System.nanoTime();
    double dt = Math.min(.05, (now - previous) / 1e9);
    previous = now;
    synchronized (game) {
      game.advance(dt);
      GLES20.glUseProgram(program);
      boolean mini = game.isMini();
      float ex = mini ? 17 : 0, ey = mini ? 28 : 34, ez = mini ? -11 : -40;
      Matrix.setLookAtM(view, 0, ex, ey, ez, 0, 0, mini ? 13 : 60, 0, 1, 0);
      // Our course coordinates use positive X to the player's right.
      for (int i = 0; i < 16; i += 4) view[i] = -view[i];
      Matrix.multiplyMM(vp, 0, projection, 0, view, 0);
      GLES20.glUniform3f(eyeLocation, ex, ey, ez);
      if (mini) miniScene();
      else rangeScene();
      for (int i = 0; i < game.trail.size(); i += 2) {
        double[] p = game.trail.get(i);
        float size = mini ? .055f : .25f;
        draw(cube, p[0], p[1] + .03, p[2], size, size, size, 0, 0xfff9e3a0);
      }
      double radius = mini ? GolfGame.BALL : Math.max(.6, game.z * .006);
      draw(disc, game.x, .09, game.z, radius * 1.3, 1, radius * 1.3, 0, 0xff456948);
      if (!game.holeComplete)
        draw(
            models.get("ball-blue"),
            game.x,
            game.y - radius,
            game.z,
            radius * 2 / .06994,
            radius * 2 / .0697,
            radius * 2 / .06994,
            0,
            0xffffffff);
      if (!game.moving && !game.holeComplete && !game.roundComplete) {
        double length = mini ? Math.min(6, game.power) : 24;
        double yaw = Math.toRadians(game.aim);
        for (int i = 1; i <= 12; i++) {
          double t = length * i / 12;
          draw(
              disc,
              game.x + Math.sin(yaw) * t,
              .1,
              game.z + Math.cos(yaw) * t,
              mini ? .065 : .35,
              1,
              mini ? .065 : .35,
              0,
              0xfff7e6b0);
        }
      }
    }
  }

  private void rangeScene() {
    draw(cube, 0, -1, 135, 170, 1, 320, 0, 0xff548965);
    for (int z = 0; z < 290; z += 25) draw(cube, 0, -.45, z, 140, .025, .17, 0, 0xff76a87c);
    for (int i = 0; i < GolfGame.TARGETS.length; i++) {
      GolfGame.Target t = GolfGame.TARGETS[i];
      draw(disc, t.x, .02, t.z, 18, 1, 18, 0, t.color);
      draw(disc, t.x, .045, t.z, 11, 1, 11, 0, 0xfff4e8c4);
      draw(disc, t.x, .07, t.z, 9.5, 1, 9.5, 0, t.color);
      draw(disc, t.x, .09, t.z, 5, 1, 5, 0, 0xfff4e8c4);
      draw(disc, t.x, .11, t.z, 3.8, 1, 3.8, 0, t.color);
      draw(models.get(i % 2 == 0 ? "flag-red" : "flag-blue"), t.x, .1, t.z, 4, 4, 4, 0, 0xffffffff);
      if (game.mode == GolfGame.Mode.COLOR_CHASE && i == game.activeTarget()) {
        double pulse = 20 + Math.sin(game.time * 3) * .6;
        draw(disc, t.x, -.02, t.z, pulse, 1, pulse, 0, 0xfffaf3d5);
      }
    }
    if (game.mode == GolfGame.Mode.DISTANCE_LADDER) {
      int distance = game.ladderDistance();
      draw(cube, 0, .04, distance - 12, 90, .035, .5, 0, 0xfff5db7d);
      draw(cube, 0, .04, distance + 12, 90, .035, .5, 0, 0xfff5db7d);
    }
    for (int side : new int[] {-1, 1}) {
      for (int z = 10; z <= 290; z += 35) {
        draw(cube, side * 73, 10, z, .5, 22, .5, 0, 0xff36554a);
        draw(cube, side * 73, 21, z + 17, .22, .22, 35, 0, 0xff81998a);
        tree(side * 85, z, 6 + (z % 3));
      }
      draw(cube, side * 73, 9, 150, .08, .08, 300, 0, 0xff8aa395);
    }
    draw(cube, 0, -.08, -3, 16, .5, 10, 0, 0xff233c3c);
    for (int x = -6; x <= 6; x += 6) draw(cube, x, .2, -2, 4, .15, 5, 0, 0xff40766a);
    for (int x = -110; x <= 110; x += 35)
      draw(cone, x, -.5, 320, 30, 22 + (x + 110) % 24, 35, 0, 0xff759c83);
    draw(models.get("structure-windmill"), -92, 0, 105, 12, 12, 12, 20, 0xffffffff);
  }

  private void miniScene() {
    boolean castle = game.mode == GolfGame.Mode.CASTLE;
    draw(cube, 0, -.8, 13, 48, .6, 62, 0, castle ? 0xff668b87 : 0xff8dad72);
    // Kenney open-course modules form the actual playing surface.
    for (int x = -5; x <= 5; x += 2)
      for (int z = 1; z <= 25; z += 2)
        draw(models.get("open"), x, -.12, z, 2, 2, 2, 0, castle ? 0xffb9ded8 : 0xffffffff);
    int rail = castle ? 0xffddd3b6 : 0xfff4e8ca;
    draw(cube, -6.2, .3, 13, .4, .8, 26.8, 0, rail);
    draw(cube, 6.2, .3, 13, .4, .8, 26.8, 0, rail);
    draw(cube, 0, .3, -.2, 12, .8, .4, 0, rail);
    draw(cube, 0, .3, 26.2, 12, .8, .4, 0, rail);
    for (GolfGame.Obstacle o : game.hole().obstacles) {
      double x = o.center(game.time);
      draw(cube, x, .25, o.z, o.w, .5, o.d, 0, o.moving ? 0xffcaa363 : 0xffa08058);
      draw(
          models.get("obstacle-block"),
          x,
          .03,
          o.z,
          o.w / .9999,
          5,
          o.d / .9999,
          0,
          o.moving ? 0xffffdca2 : 0xffffffff);
    }
    for (GolfGame.Obstacle w : game.hole().water) {
      draw(cube, w.x, .06, w.z, w.w, .07, w.d, 0, 0xff448fb8);
      for (int i = 0; i < 3; i++)
        draw(
            cube,
            w.x + Math.sin(game.time + i) * .2,
            .11,
            w.z - w.d * .3 + i * w.d * .3,
            w.w * .65,
            .02,
            .045,
            0,
            0xff8ed2d4);
    }
    draw(disc, game.hole().cupX, .055, game.hole().cupZ, .69, 1, .69, 0, 0xff284c3c);
    draw(disc, game.hole().cupX, .065, game.hole().cupZ, .53, 1, .53, 0, 0xff142b2b);
    draw(
        models.get(castle ? "flag-blue" : "flag-red"),
        game.hole().cupX + .25,
        .1,
        game.hole().cupZ + .12,
        1.8,
        1.8,
        1.8,
        0,
        0xffffffff);
    draw(disc, 0, .06, 2, .55, 1, .55, 0, 0xffe9d798);
    if (castle) {
      draw(models.get("castle"), -9, -.2, 21, 6, 7, 6, 0, 0xffffffff);
      draw(models.get("castle"), 9, -.2, 24, 5, 6, 5, 0, 0xffffffff);
      draw(models.get("structure-gate"), 0, .03, 27.5, 4, 5, 3, 0, 0xffffffff);
    } else {
      draw(models.get("structure-windmill"), -9, 0, 17, 4, 4, 4, 20, 0xffffffff);
      draw(models.get("structure-windmill"), 9, 0, 25, 3, 3, 3, -20, 0xffffffff);
    }
    for (int i = 0; i < 6; i++) {
      tree(-11 - (i % 2) * 2, i * 6 - 3, 2.7 + (i % 2));
      tree(11 + (i % 2) * 2, i * 6 - 3, 2.5 + (i % 3) * .5);
    }
  }

  private void tree(double x, double z, double height) {
    draw(cube, x, height * .25, z, .4, height * .5, .4, 0, 0xff856449);
    draw(cone, x, height * .3, z, height * .45, height, height * .45, 0, 0xff40775c);
  }

  private void draw(
      Mesh mesh,
      double x,
      double y,
      double z,
      double sx,
      double sy,
      double sz,
      float angle,
      int tint) {
    if (mesh == null) return;
    Matrix.setIdentityM(model, 0);
    Matrix.translateM(model, 0, (float) x, (float) y, (float) z);
    Matrix.rotateM(model, 0, angle, 0, 1, 0);
    Matrix.scaleM(model, 0, (float) sx, (float) sy, (float) sz);
    Matrix.multiplyMM(mvp, 0, vp, 0, model, 0);
    GLES20.glUniformMatrix4fv(mvpLocation, 1, false, mvp, 0);
    GLES20.glUniformMatrix4fv(modelLocation, 1, false, model, 0);
    GLES20.glUniform3f(
        tintLocation, ((tint >> 16) & 255) / 255f, ((tint >> 8) & 255) / 255f, (tint & 255) / 255f);
    mesh.data.position(0);
    GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 36, mesh.data);
    GLES20.glEnableVertexAttribArray(position);
    mesh.data.position(3);
    GLES20.glVertexAttribPointer(normal, 3, GLES20.GL_FLOAT, false, 36, mesh.data);
    GLES20.glEnableVertexAttribArray(normal);
    mesh.data.position(6);
    GLES20.glVertexAttribPointer(color, 3, GLES20.GL_FLOAT, false, 36, mesh.data);
    GLES20.glEnableVertexAttribArray(color);
    GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mesh.count);
  }

  private static final class Mesh {
    final FloatBuffer data;
    final int count;

    Mesh(float[] values) {
      count = values.length / 9;
      data =
          ByteBuffer.allocateDirect(values.length * 4)
              .order(ByteOrder.nativeOrder())
              .asFloatBuffer();
      data.put(values).position(0);
    }
  }

  private Mesh load(String name) throws IOException {
    try (InputStream in = context.getAssets().open("models/" + name + ".mesh");
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] b = new byte[8192];
      int n;
      while ((n = in.read(b)) != -1) out.write(b, 0, n);
      ByteBuffer bytes = ByteBuffer.wrap(out.toByteArray()).order(ByteOrder.LITTLE_ENDIAN);
      int count = bytes.getInt();
      if (count < 1 || count > 1000000 || bytes.remaining() != count * 36)
        throw new IOException("Invalid mesh " + name);
      float[] values = new float[count * 9];
      bytes.asFloatBuffer().get(values);
      return new Mesh(values);
    }
  }

  private void vertex(ArrayList<Float> a, float x, float y, float z, float nx, float ny, float nz) {
    for (float v : new float[] {x, y, z, nx, ny, nz, 1, 1, 1}) a.add(v);
  }

  private Mesh mesh(ArrayList<Float> a) {
    float[] f = new float[a.size()];
    for (int i = 0; i < f.length; i++) f[i] = a.get(i);
    return new Mesh(f);
  }

  private Mesh makeCube() {
    ArrayList<Float> a = new ArrayList<>();
    float[][] points = {
      {-.5f, -.5f, -.5f},
      {.5f, -.5f, -.5f},
      {.5f, .5f, -.5f},
      {-.5f, .5f, -.5f},
      {-.5f, -.5f, .5f},
      {.5f, -.5f, .5f},
      {.5f, .5f, .5f},
      {-.5f, .5f, .5f}
    };
    int[][] faces = {
      {0, 3, 2, 1}, {5, 6, 7, 4}, {4, 7, 3, 0}, {1, 2, 6, 5}, {3, 7, 6, 2}, {4, 0, 1, 5}
    };
    float[][] normals = {{0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}, {0, 1, 0}, {0, -1, 0}};
    for (int face = 0; face < 6; face++)
      for (int i : new int[] {0, 1, 2, 0, 2, 3}) {
        float[] p = points[faces[face][i]], n = normals[face];
        vertex(a, p[0], p[1], p[2], n[0], n[1], n[2]);
      }
    return mesh(a);
  }

  private Mesh makeRound(boolean cone) {
    ArrayList<Float> a = new ArrayList<>();
    for (int i = 0; i < 48; i++) {
      double angle = i * Math.PI / 24, next = (i + 1) * Math.PI / 24;
      float x = (float) Math.cos(angle),
          z = (float) Math.sin(angle),
          xx = (float) Math.cos(next),
          zz = (float) Math.sin(next);
      if (cone) {
        float nx = (x + xx) * .45f, nz = (z + zz) * .45f;
        vertex(a, 0, 1, 0, nx, .45f, nz);
        vertex(a, x, 0, z, nx, .45f, nz);
        vertex(a, xx, 0, zz, nx, .45f, nz);
      } else {
        vertex(a, 0, 0, 0, 0, 1, 0);
        vertex(a, x, 0, z, 0, 1, 0);
        vertex(a, xx, 0, zz, 0, 1, 0);
      }
    }
    return mesh(a);
  }
}
