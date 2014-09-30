package org.rascalmpl.library.lang.java.m3.internal;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.imp.pdb.facts.IListWriter;
import org.eclipse.imp.pdb.facts.IMap;
import org.eclipse.imp.pdb.facts.IMapWriter;
import org.eclipse.imp.pdb.facts.ISourceLocation;
import org.eclipse.imp.pdb.facts.IString;
import org.eclipse.imp.pdb.facts.IValue;
import org.eclipse.imp.pdb.facts.IValueFactory;

public class ClassPaths {
	private final IValueFactory vf;
	
	public ClassPaths(IValueFactory vf) {
		this.vf = vf;
	}
	
	public IMap getClassPath(ISourceLocation directory, IMap dependencyUpdateSites, ISourceLocation mavenExecutable) throws UnsupportedOperationException, BuildException {
		assert directory.getScheme().equals("file");
		assert mavenExecutable.getScheme().equals("file");

		BuildManager bmw = new BuildManager(mavenExecutable.getPath());
		HashMap<String, String> dependencies = new HashMap<>();

		for (IValue id: dependencyUpdateSites) {
			dependencies.put(((IString) id).getValue(), ((ISourceLocation) dependencyUpdateSites.get(id)).getURI().toString());
		}

		bmw.addEclipseRepositories(dependencies);

		Map<File, List<String>> workspaceClasspath = bmw.getWorkspaceClasspath(new File(directory.getPath()));

		IMapWriter mw = vf.mapWriter();
		for (Entry<File, List<String>> e : workspaceClasspath.entrySet()) {
			IListWriter list = vf.listWriter();

			for (String jar : e.getValue()) {
				list.append(vf.sourceLocation(jar));
			}

			mw.put(vf.sourceLocation(e.getKey().getAbsolutePath()), list.done());
		}

		return mw.done();
	}
}