module lang::java::m3::ClassPaths

@javaClass{org.rascalmpl.library.lang.java.m3.internal.ClassPaths}
java map[loc,list[loc]] getClassPath(
  loc workspace, 
  map[str,loc] updateSites = (x : |http://download.eclipse.org/releases| + x | x <- ["juno","kepler","luna"]),
  loc mavenExecutable = |file:///usr/local/bin/mvn|);