/*
 * Copyright (c) 2023, WSO2 LLC. (http://wso2.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ballerinalang.maven.bala.client;


import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.codehaus.plexus.util.WriterFactory;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.layout.RepositoryLayout;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutProvider;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.spi.connector.transport.TransporterProvider;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.artifact.SubArtifact;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.eclipse.aether.version.Version;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;


/**
 * Maven bala dependency resolving.
 *
 * @since 2201.8.0
 */

public class MavenResolverClient {
    public static final String PLATFORM = "platform";
    public static final String BALA_EXTENSION = "bala";
    public static final String POM = "pom";
    public static final String DEFAULT_REPO = "default";
    public static final String ARTIFACT_SEPERATOR = "-";
    // Fixed name the SBOM is uploaded under, matching ProjectConstants.BOM_JSON in the ballerina-lang module
    // (duplicated here since this module does not depend on ballerina-lang). Uploaded as a raw file via
    // Transporter rather than as a classified Maven artifact, so it keeps this exact name in the repository
    // instead of being renamed to <artifactId>-<version>-<classifier>.<extension> per Maven layout conventions.
    public static final String SBOM_FILE_NAME = "bom.cdx.json";
    RepositorySystem system;
    DefaultRepositorySystemSession session;
    RepositoryLayoutProvider repositoryLayoutProvider;
    TransporterProvider transporterProvider;

    RemoteRepository.Builder repository;

    /**
     * Resolver will be initialized to specified to location.
     */
    public MavenResolverClient() {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
        system = locator.getService(RepositorySystem.class);
        repositoryLayoutProvider = locator.getService(RepositoryLayoutProvider.class);
        transporterProvider = locator.getService(TransporterProvider.class);
        session = MavenRepositorySystemUtils.newSession();
    }

    /**
     * Resolves provided artifact into resolver location.
     *
     * @param groupId    group ID of the dependency
     * @param artifactId artifact ID of the dependency
     * @param version    version of the dependency
     * @throws MavenResolverClientException when specified dependency cannot be resolved
     */
    public void pullPackage(String groupId, String artifactId, String version, String targetLocation) throws
            MavenResolverClientException {

        LocalRepository localRepo = new LocalRepository(targetLocation);
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
        Artifact artifact = new DefaultArtifact(groupId, artifactId, BALA_EXTENSION, version);
        try {
            session.setTransferListener(new TransferListenerForClient());
            ArtifactRequest artifactRequest = new ArtifactRequest();
            artifactRequest.setArtifact(artifact);
            artifactRequest.addRepository(repository.build());
            system.resolveArtifact(session, artifactRequest);
        } catch (ArtifactResolutionException e) {
            throw new MavenResolverClientException(e.getMessage());
        }
    }


    /**
     * Deploys provided artifact into the repository.
     * @param balaPath      path to the bala
     * @param orgName       organization name
     * @param packageName   package name
     * @param version       version of the package
     * @throws MavenResolverClientException when deployment fails
     */
    public void pushPackage(Path balaPath, String orgName, String packageName, String version, Path localRepoPath)
            throws MavenResolverClientException {
        pushPackage(balaPath, orgName, packageName, version, localRepoPath, null);
    }

    /**
     * Deploys the provided artifact, together with its SBOM, into the repository.
     *
     * <p>The SBOM is uploaded separately as a raw file under the fixed name {@value #SBOM_FILE_NAME}, next to
     * the bala/pom, rather than as a classified Maven artifact via {@link SubArtifact}. A classified artifact
     * would be renamed by Maven's repository layout to {@code <artifactId>-<version>-<classifier>.<extension>};
     * uploading it directly through the repository's {@link Transporter} instead keeps this exact file name, at
     * the cost of it no longer being resolvable via Maven GAV+classifier coordinates — a consumer needs to know
     * this fixed relative path convention to fetch it back.</p>
     *
     * @param balaPath      path to the bala
     * @param orgName       organization name
     * @param packageName   package name
     * @param version       version of the package
     * @param localRepoPath path to the local Maven repository used during deployment
     * @param sbomPath      path to the SBOM file to upload, or {@code null} to skip uploading one
     * @throws MavenResolverClientException when deployment fails
     */
    public void pushPackage(Path balaPath, String orgName, String packageName, String version, Path localRepoPath,
                             Path sbomPath) throws MavenResolverClientException {
        LocalRepository localRepo = new LocalRepository(localRepoPath.toAbsolutePath().toString());
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
        DeployRequest deployRequest = new DeployRequest();
        RemoteRepository remoteRepository = this.repository.build();
        deployRequest.setRepository(remoteRepository);
        Artifact mainArtifact = new DefaultArtifact(
                orgName, packageName, BALA_EXTENSION, version).setFile(balaPath.toFile());
        deployRequest.addArtifact(mainArtifact);
        try {
            File temporaryPom = generatePomFile(orgName, packageName, version);
            deployRequest.addArtifact(new SubArtifact(mainArtifact, "", POM, temporaryPom));
            system.deploy(session, deployRequest);
            if (sbomPath != null && Files.isRegularFile(sbomPath)) {
                uploadRawFile(remoteRepository, mainArtifact, sbomPath, SBOM_FILE_NAME);
            }
        } catch (DeploymentException | IOException e) {
            throw new MavenResolverClientException(e.getMessage());
        }
    }

    /**
     * Uploads a file to the given remote repository at the location of {@code baseArtifact}, under {@code
     * fileName}, bypassing Maven's coordinate-based artifact naming.
     *
     * @param remoteRepository repository to upload to
     * @param baseArtifact     artifact whose directory the file is uploaded alongside
     * @param sourceFile       file to upload
     * @param fileName         name to give the file in the repository
     * @throws MavenResolverClientException when the layout/transporter cannot be resolved or the upload fails
     */
    private void uploadRawFile(RemoteRepository remoteRepository, Artifact baseArtifact, Path sourceFile,
                               String fileName) throws MavenResolverClientException {
        try {
            RepositoryLayout layout = repositoryLayoutProvider.newRepositoryLayout(session, remoteRepository);
            URI artifactLocation = layout.getLocation(baseArtifact, true);
            URI fileLocation = artifactLocation.resolve(fileName);
            Transporter transporter = transporterProvider.newTransporter(session, remoteRepository);
            try {
                transporter.put(new PutTask(fileLocation).setDataFile(sourceFile.toFile()));
            } finally {
                transporter.close();
            }
        } catch (Exception e) {
            throw new MavenResolverClientException(e.getMessage());
        }
    }

    /**
     * Get all versions of a package from the Maven repository.
     *
     * @param groupId       group ID of the package
     * @param artifactId    artifact ID of the package
     * @param localRepoPath path to the local Maven repository
     * @return list of version strings
     * @throws MavenResolverClientException when version resolution fails
     */
    public List<String> getPackageVersions(String groupId, String artifactId, Path localRepoPath) throws
            MavenResolverClientException {
        LocalRepository localRepo = new LocalRepository(localRepoPath.toAbsolutePath().toString());
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
        session.setOffline(false);
        session.setUpdatePolicy(RepositoryPolicy.UPDATE_POLICY_ALWAYS);

        Artifact artifact = new DefaultArtifact(groupId, artifactId, BALA_EXTENSION, "[0,)");
        VersionRangeRequest versionRangeRequest = new VersionRangeRequest();
        versionRangeRequest.setArtifact(artifact);
        versionRangeRequest.addRepository(repository.build());

        try {
            VersionRangeResult versionRangeResult = system.resolveVersionRange(session, versionRangeRequest);
            return versionRangeResult.getVersions().stream()
                    .map(Version::toString)
                    .collect(Collectors.toList());
        } catch (VersionRangeResolutionException e) {
            throw new MavenResolverClientException(e.getMessage());
        }
    }

    /**
     * Specified repository will be added to remote repositories.
     *
     * @param id  identifier of the repository
     * @param url url of the repository
     */
    public void addRepository(String id, String url) {
        this.repository = new RemoteRepository.Builder(id, DEFAULT_REPO, url);
    }

    /**
     * Specified repository will be added to remote repositories.
     *
     * @param id       identifier of the repository
     * @param url      url of the repository
     * @param username username which has authentication access
     * @param password password which has authentication access
     */
    public void addRepository(String id, String url, String username, String password) {
        Authentication authentication =
                new AuthenticationBuilder()
                        .addUsername(username)
                        .addPassword(password)
                        .build();
        this.repository = new RemoteRepository.Builder(id, DEFAULT_REPO, url)
                .setAuthentication(authentication);
    }

    /**
     * Proxy will be set to the repository.
     * @param url url of the proxy
     * @param port port of the proxy
     * @param username username of the proxy
     * @param password password of the proxy
     */
    public void setProxy(String url, int port, String username, String password) {
        if (url.isEmpty() || port == 0) {
            return;
        }

        Proxy proxy;
        if ((!(username).isEmpty() && !(password).isEmpty())) {
            Authentication authentication =
                    new AuthenticationBuilder()
                            .addUsername(username)
                            .addPassword(password)
                            .build();
            proxy = new Proxy(null, url, port, authentication);
        } else {
            proxy = new Proxy(null, url, port);
        }

        this.repository.setProxy(proxy);
    }

    private File generatePomFile(String groupId, String artifactId, String version) throws IOException {
        Model model = new Model();
        model.setModelVersion("4.0.0");
        model.setGroupId(groupId);
        model.setArtifactId(artifactId);
        model.setVersion(version);
        model.setPackaging(BALA_EXTENSION);
        File tempFile = File.createTempFile(groupId + ARTIFACT_SEPERATOR + artifactId + ARTIFACT_SEPERATOR +
                version, "." + POM);
        tempFile.deleteOnExit();
        Writer fw = WriterFactory.newXmlWriter(tempFile);
        new MavenXpp3Writer().write(fw, model);
        fw.close();
        return tempFile;
    }
}
