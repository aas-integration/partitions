package com.vesperin.partition.utils;

import com.google.common.collect.Sets;
import com.vesperin.text.spelling.StopWords;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * @author Huascar Sanchez
 */
public class WordMaker {
  private static final Set<String> GROUP_ONE = Sets.newHashSet("boofcv", "catalano-framework", "imglib2", "imgscalr", "imagej", "thumbnailinator");
  private static final Set<String> GROUP_TWO = Sets.newHashSet("dyn4j", "jreactphysics3d", "react", "jmonkeyengine", "jbox2d");

  private static final Set<String> GLOSSARY_ONE = Sets.newHashSet("addition", "area",
    "aspect", "ratio", "greyscale", "mapping", "bayer", "filter", "bilinear",
    "interpolation", "bitmap", "image", "blurring", "bounding", "brightness",
    "transformation", "transformer", "capture", "ccd", "closing", "colour", "model", "composition",
    "contrast", "expansion", "convolution", "corrupted", "pixel", "cropping", "diagonal",
    "digital", "camera", "image", "dilation", "edge", "map", "mask",
    "spectrum", "electromagnetic", "erosion", "flipping", "flip", "frame", "noise",
    "gaussian", "geometric", "halftoning", "highlight", "histogram", "equalisation",
    "hsb", "hue", "process", "processing", "interpolation", "inversion", "kernel",
    "line", "lut", "median", "midtone", "midtones", "morphological", "nearest", "neighbour",
    "neighbor", "ntsc", "opening", "outlying", "output", "restoration", "point",
    "posterisation", "quantization", "ramp", "raw", "resolution", "rgb", "roof",
    "hole", "sample", "sampling", "scale", "factor", "saturation", "scaling", "scanner",
    "electron", "microscope", "shadow", "subtraction", "thresholding", "thermal", "box",
    "mirror", "ellipse");

  private static final Set<String> GLOSSARY_TWO = Sets.newHashSet("anisotropic",
    "friction", "collision", "detection", "center", "mass", "restitution", "coefficient",
    "coherence", "contact", "generation", "dynamic", "force", "engine", "halfsize", "half",
    "size", "homeheneous", "identity", "matrix", "impulse", "tensor", "inertia", "isotropic",
    "constraint", "solver", "iterative", "jacobian", "vector", "static", "spatial",
    "speed", "spring", "angular", "velocity", "projectile", "bsp", "plane", "tree",
    "sphere", "insertion", "buoyancy", "sailing", "sail", "simulator", "bhv", "differential",
    "position", "coarse", "aggregate", "pipeline", "spatial", "octtree", "oct", "grid", "quadtree",
    "quad", "torque", "interpenetration", "micro", "resting", "time", "division", "wave",
    "concussive", "explosion", "propagation", "cone", "geometry", "assembly", "primitive", "depth",
    "penetration", "relative", "body", "colliding", "polyhedra", "convection", "chimney",
    "equation", "volume", "joint", "particle", "directx", "handedness", "drag", "elasticity",
    "euler", "angle", "implosion", "fireworks", "simulation", "fluid", "flow", "flight", "rigid",
    "physics", "hook", "", "render", "renderer", "collision", "detection", "detector", "detect",
    "movement", "move", "moving", "gravity", "object", "aliasing", "blending", "alpha", "animation",
    "anisotropic", "array", "asset", "bake", "batching", "behavior", "boo", "clip", "collider",
    "event", "crosshair", "cubemap", "cut", "scene", "damping", "extrapolation", "fbx",
    "fading", "polygon", "motion", "cylinder", "cuboid", "sphere", "cone", "entropy");

  private WordMaker() {}

  private static Set<String> getGlossaryOne() {
    return GLOSSARY_ONE;
  }

  private static Set<String> getGlossaryTwo(){
    return GLOSSARY_TWO;
  }

  public static Set<StopWords> generateStopWords(String name){

    final String lowercase = name.toLowerCase(Locale.ENGLISH);

    final StopWords english = StopWords.ENGLISH;

    Arrays.asList("ifd", "make", "wtum", "slic", "zhang", "three",
      "rodrigue", "estimate", "il", "coef", "nothing", "omni", "webcam",
      "canny", "association", "fundamental", "example", "se", "nto",
      "associated", "naive", "cloud", "alg", "weighted",
      "five", "enhanced", "purpose", "just", "brief", "dda",
      "pto", "peak", "prune", "mean", "essentially", "extremely", "benefit",
      "analysis", "otsu", "moment", "matching", "started", "student", "human",
      "argbargb", "ntree", "arg", "dna", "ssaoui", "gle", "ik", "ir",
      "al", "hello", "fxaa", "tga", "recalc", "tu", "arff", "icon", "sfot",
      "uv", "lru", "ssao", "efx", "lepetit", "harri", "igle", "dof", "ogle", "like",
      "udp", "canva", "six", "fault", "codec", "combined", "perspective", "triangulate",
      "radial", "shape", "mjpeg", "improve", "rotate", "tracking", "jogl"

    ).forEach(english::add);


    final StopWords general = StopWords.GENERAL;
    if(GROUP_ONE.contains(lowercase)){
      getGlossaryTwo().forEach(general::add);
    } else if(GROUP_TWO.contains(lowercase)){
      getGlossaryOne().forEach(general::add);
    }

    return Sets.newHashSet(english, StopWords.JAVA, general);
  }


}
