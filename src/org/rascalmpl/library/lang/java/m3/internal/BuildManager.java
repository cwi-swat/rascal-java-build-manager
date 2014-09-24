package org.rascalmpl.library.lang.java.m3.internal;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
	private static final String MAVEN_CLASSPATH_TXT = "mavenClasspath.txt";
	private Map<String, String> eclipseRepos = new HashMap<>();
	
	public BuildManager() {
		eclipseRepos.put("juno", "http://download.eclipse.org/releases/juno");
		eclipseRepos.put("kepler", "http://download.eclipse.org/releases/kepler");
		eclipseRepos.put("luna", "http://download.eclipse.org/releases/luna");
	}
	
	public void addEclipseRepositories(Map<String, String> repos) {
		eclipseRepos.putAll(repos);
	}
	
	/**
	 * Uses maven and Tycho to compute the classpaths of all projects residing the workdirectory
	 * @param workingDirectory
	 * @return
	 * @throws BuildException
	 */
	public Map<File,List<String>> getWorkspaceClasspath(File workingDirectory) throws BuildException {
		final String MAVEN_EXECUTABLE = System.getProperty("MAVEN_EXECUTABLE");
		if (MAVEN_EXECUTABLE == null) {
			throw new BuildException("Maven executable system property not set");
		}
		
		if (!pomExists(workingDirectory) && isEclipseProjectRoot(workingDirectory)) {
			generatePOMFile(workingDirectory, MAVEN_EXECUTABLE);
		}
		
		if (pomExists(workingDirectory)) {
			return retrieveClasspath(workingDirectory, MAVEN_EXECUTABLE);
		}
		else {
			return Collections.emptyMap();
		}
	}

	private boolean pomExists(File workingDirectory) {
		return pomFile(workingDirectory).exists();
	}

	private Map<File,List<String>> retrieveClasspath(File workingDirectory, String MAVEN_EXECUTABLE) throws BuildException {
		// Tycho does its magic here and writes a file into every subdirectory of workDirectory which is a maven project
		ProcessBuilder pb = new ProcessBuilder(MAVEN_EXECUTABLE, "dependency:build-classpath", "-Dmdep.outputFile=" + MAVEN_CLASSPATH_TXT);
		
		pb.directory(workingDirectory);

		try {
			Process process = pb.start();
			if (process.waitFor() != 0) {
				throw new BuildException("Retrieving classpath from maven failed because maven exited with a non-zero exit status");
			}
			
			Map<File,List<String>> result = new HashMap<>();
			
			// now we read the magically constructed files and get the classpath information we need
			for (File folder : workingDirectory.listFiles()) {
				try (BufferedReader br = new BufferedReader(new FileReader(new File(folder, MAVEN_CLASSPATH_TXT)))) {
					StringBuilder builder = new StringBuilder();
					String line = null;
					while ( (line = br.readLine()) != null) {
						builder.append(line);
					}

					result.put(folder, Arrays.asList(builder.toString().split(";")));	
				}
			}
			
			return result;
		} 
		catch (IOException | InterruptedException e) {
			throw new BuildException("Retrieving classpath from maven failed unexpectedly.", e);
		}
	}

	private void generatePOMFile(File workingDirectory, String MAVEN_EXECUTABLE) throws BuildException {
		File pomFile = pomFile(workingDirectory);
		String groupID = workingDirectory.getName();
		
		if (!pomFile.exists()) {
			ProcessBuilder pb = new ProcessBuilder(MAVEN_EXECUTABLE, "org.eclipse.tycho:tycho-pomgenerator-plugin:generate-poms", "-DgroupId="+groupID);

			pb.directory(workingDirectory);

			try {
				if (pb.start().waitFor() != 0) {
					throw new BuildException("Maven/Tycho generated non-zero exit value while generating pom");
				}
				
				rewritePOM(pomFile);
			} 
			catch (IOException | InterruptedException e) {
				throw new BuildException("Could not generate pom.xml file", e);
			}
		}
	}

	private File pomFile(File workingDirectory) {
		return new File(workingDirectory, "pom.xml");
	}

	public boolean isEclipseProjectRoot(File workingDirectory) {
		Path startingDir = Paths.get(workingDirectory.getAbsolutePath());
		String pattern = "{.project,.classpath,MANIFEST.MF}";
		
		Finder finder = new Finder(pattern);
		try {
			Set<FileVisitOption> options = Collections.emptySet();
			Files.walkFileTree(startingDir, options, Integer.MAX_VALUE, finder);
		} 
		catch (IOException e) {
			return false;
		}
		
		return finder.done();
	}

	private void rewritePOM(File pomFile) throws BuildException {
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
			throw new BuildException("POM rewriting (to add plugin dependencies, cause) failed unexpectedly", e);
		}
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
