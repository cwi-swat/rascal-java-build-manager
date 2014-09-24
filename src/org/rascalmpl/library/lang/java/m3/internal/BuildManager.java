package org.rascalmpl.library.lang.java.m3.internal;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.maven.model.Build;
//import org.apache.maven.cli.MavenCli;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Repository;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

public class BuildManager {
	private Map<String, String> eclipseRepos = new HashMap<>();
	
	public BuildManager() {
		eclipseRepos.put("juno", "http://download.eclipse.org/releases/juno");
		eclipseRepos.put("kepler", "http://download.eclipse.org/releases/kepler");
		eclipseRepos.put("luna", "http://download.eclipse.org/releases/luna");
	}
	
	public void addEclipseRepositories(Map<String, String> repos) {
		eclipseRepos.putAll(repos);
	}
	
	public int resolveProject(String workingDirectory) throws Exception {
		final String MAVEN_EXECUTABLE = System.getProperty("MAVEN_EXECUTABLE");
		
		if (MAVEN_EXECUTABLE == null) {
			throw new Exception("Maven executable system property not set");
		}
		
		if (workingDirectory.endsWith("/")) {
			workingDirectory = workingDirectory.substring(0, workingDirectory.length()-1);
		}
		
		File typeChecker = new File(workingDirectory + "/pom.xml");
		String groupID = workingDirectory.substring(workingDirectory.lastIndexOf('/')+1);
		int result = 0;
		
		if (!typeChecker.exists()) {
			if (isEclipseProjectRoot(workingDirectory)) {
				ProcessBuilder pb = new ProcessBuilder(MAVEN_EXECUTABLE, "org.eclipse.tycho:tycho-pomgenerator-plugin:generate-poms", "-DgroupId="+groupID);

				pb.directory(new File(workingDirectory));
				
				try {
					Process p = pb.start();
					p.waitFor();
					result = p.exitValue();
					result = rewritePOM(workingDirectory);
				} 
				catch (IOException | InterruptedException e) {
					result = -1;
				}
			} else {
				// not handling normal projects
				result = -1;
			}
		}
		
		if (result == 0) {
			ProcessBuilder pb = new ProcessBuilder(MAVEN_EXECUTABLE, "dependency:build-classpath", "-Dmdep.outputFile=cp.txt");
			pb.directory(new File(workingDirectory));

			try {
				Process p = pb.start();
				p.waitFor();
				result = p.exitValue();
			} 
			catch (IOException | InterruptedException e) {
				result = -1;
			}
		}
		return result;
	}

	private boolean isEclipseProjectRoot(String workingDirectory) {
		Path startingDir = Paths.get(workingDirectory);
		String pattern = "{.project,.classpath,MANIFEST.MF}";
		
		Finder finder = new Finder(pattern);
		try {
			Set<FileVisitOption> options = Collections.emptySet();
			Files.walkFileTree(startingDir, options, Integer.MAX_VALUE, finder);
		} catch (IOException e) {
			return false;
		}
		return finder.done();
	}

	private int rewritePOM(String workingDirectory) {
		String pomFile = workingDirectory + "/pom.xml";
		try (Reader reader = new FileReader(pomFile)) {
			MavenXpp3Reader pomReader = new MavenXpp3Reader();
			
			Model model = pomReader.read(reader);
			reader.close();
			
			model.addRepository(createRepo("maven_central", "http://repo.maven.apache.org/maven2/", "default"));
			
			for (String id: eclipseRepos.keySet()) {
				model.addRepository(createRepo(id, eclipseRepos.get(id), "p2"));
			}
			
			Build modelBuild = model.getBuild();
			if (modelBuild == null) {
				model.setBuild(new Build());
			}
			
			model.getBuild().addPlugin(createPlugin("org.eclipse.tycho", "tycho-maven-plugin", "0.21.0", true));
			model.getBuild().addPlugin(createPlugin("org.eclipse.tycho", "target-platform-configuration", "0.21.0", false));
			model.getBuild().addPlugin(createPlugin("org.apache.maven.plugins", "maven-dependency-plugin", "2.8", false));
			
			MavenXpp3Writer pomWriter = new MavenXpp3Writer();
			pomWriter.write(new FileWriter(pomFile), model);
		} 
		catch (IOException | XmlPullParserException e) {
			return -1;
		}
		return 0;
	}

	private Plugin createPlugin(String groupId, String artifactId, String version, boolean extension) {
		Plugin plugin = new Plugin();
		plugin.setGroupId(groupId);
		plugin.setArtifactId(artifactId);
		plugin.setVersion(version);
		if (extension) {
			plugin.setExtensions(true);
		}
		return plugin;
	}

	private Repository createRepo(String id, String url, String layout) {
		Repository repo = new Repository();
		repo.setId(id);
		repo.setUrl(url);
		repo.setLayout(layout);
		return repo;
	}
}
