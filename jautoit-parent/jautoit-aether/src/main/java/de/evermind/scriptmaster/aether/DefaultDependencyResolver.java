package de.evermind.scriptmaster.aether;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;

public class DefaultDependencyResolver implements DependencyResolver {

	private final DependencyConfiguration cfg;
	private final RepositorySystem repositorySystem;
	private final DefaultRepositorySystemSession session;

	public DefaultDependencyResolver() {
		this(DependencyConfiguration.getMavenDefault());
	}

	public DefaultDependencyResolver(DependencyConfiguration cfg) {
		this.cfg = DependencyConfiguration.getMavenDefault();

		repositorySystem = newRepositorySystem();
		session = MavenRepositorySystemUtils.newSession();

		final LocalRepository local = new LocalRepository(cfg.getLocalRepository());
		session.setLocalRepositoryManager(repositorySystem.newLocalRepositoryManager(session, local));

		session.setTransferListener(new ConsoleTransferListener());
		session.setRepositoryListener(new ConsoleRepositoryListener());

	}

	/**
	 * Implements the Dependency resolve mechanism with aether.
	 */
	@Override
	public Set<Path> resolve(Collection<Dependency> dependencies) throws IOException {
		DependencyResult result;
		try {
			result = resolveDependencies(dependencies);
		} catch (RepositoryException e) {
			throw new IOException(e);
		}
		return getPaths(result);
	}

	public CollectResult collectDependencies(Collection<Dependency> dependencies) throws RepositoryException {
		CollectRequest collectRequest = newCollectRequest(dependencies);
		return repositorySystem.collectDependencies(session, collectRequest);
	}

	public DependencyResult resolveDependencies(Collection<Dependency> dependencies) throws RepositoryException {
		CollectRequest collectRequest = newCollectRequest(dependencies);
		DependencyFilter classpathFlter = DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE);
		DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, classpathFlter);
		return repositorySystem.resolveDependencies(session, dependencyRequest);
	}

	public Set<Path> getPaths(DependencyResult dependencyResult) {
		return dependencyResult.getArtifactResults().stream().//
				map(a -> a.getArtifact().getFile().toPath()).//
				collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private static RepositorySystem newRepositorySystem() {
		DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
		locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
		locator.addService(TransporterFactory.class, FileTransporterFactory.class);
		locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
		return locator.getService(RepositorySystem.class);
	}

	private CollectRequest newCollectRequest(Collection<Dependency> dependencies) {
		return new CollectRequest(
				dependencies.stream().map(DefaultDependencyResolver::toAether).collect(Collectors.toList()), //
				null, cfg.getRemoteRepositories());
	}

	private static org.eclipse.aether.graph.Dependency toAether(Dependency dependency) {
		String ext = dependency.getExt();
		if (ext.isEmpty()) {
			ext = "jar";
		}
		Artifact artifact = new DefaultArtifact(dependency.getGroup(), dependency.getName(), ext,
				dependency.getVersion());
		return new org.eclipse.aether.graph.Dependency(artifact, JavaScopes.COMPILE);
	}

}