/*
 *  Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package io.ballerina.projects;

import io.ballerina.projects.internal.DependencyManifestBuilder;
import io.ballerina.projects.util.ProjectConstants;
import io.ballerina.tools.diagnostics.Diagnostic;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static io.ballerina.projects.BallerinaTomlTests.getPackageManifest;

/**
 * Test DependenciesToml.
 */
public class DependenciesTomlTests {

    private static final Path RESOURCE_DIRECTORY = Paths.get("src", "test", "resources");
    private static final Path DEPENDENCIES_TOML_REPO = RESOURCE_DIRECTORY.resolve("dependencies-toml");
    static final PrintStream OUT = System.out;

    @Test
    public void testValidDependenciesToml() throws IOException {
        DependencyManifest depsManifest = getDependencyManifest(
                DEPENDENCIES_TOML_REPO.resolve("dependencies-valid.toml"));

        depsManifest.diagnostics().errors().forEach(OUT::println);
        Assert.assertFalse(depsManifest.diagnostics().hasErrors());

        List<DependencyManifest.Package> dependencies = new ArrayList<>(depsManifest.packages());
        Assert.assertEquals(depsManifest.dependenciesTomlVersion(), "2");
        Assert.assertEquals(dependencies.size(), 3);
        Assert.assertEquals(depsManifest.distributionVersion().toString(), "2201.5.0-20230328-002700-db7a81cf");

        DependencyManifest.Package rootPackage = dependencies.get(0);
        Assert.assertEquals(rootPackage.org().value(), "winery");
        Assert.assertEquals(rootPackage.name().value(), "foo");
        Assert.assertEquals(rootPackage.version().toString(), "1.0.0");
        Assert.assertFalse(rootPackage.isTransitive());
        Assert.assertEquals(rootPackage.dependencies().size(), 1);
        Assert.assertEquals(rootPackage.modules().size(), 1);

        DependencyManifest.Package twitter = dependencies.get(1);
        Assert.assertEquals(twitter.org().value(), "wso2");
        Assert.assertEquals(twitter.name().value(), "twitter");
        Assert.assertEquals(twitter.version().toString(), "2.3.4");
        Assert.assertFalse(twitter.isTransitive());
        List<DependencyManifest.Dependency> twitterTransDependencies = new ArrayList<>(twitter.dependencies());
        Assert.assertEquals(twitterTransDependencies.size(), 5);

        DependencyManifest.Dependency twitterFirstTransDependency = twitterTransDependencies.get(0);
        Assert.assertEquals(twitterFirstTransDependency.org().value(), "ballerina");
        Assert.assertEquals(twitterFirstTransDependency.name().value(), "jballerina.java");
        DependencyManifest.Dependency twitterLastTransDependency = twitterTransDependencies.get(4);
        Assert.assertEquals(twitterLastTransDependency.org().value(), "ballerina");
        Assert.assertEquals(twitterLastTransDependency.name().value(), "time");

        List<DependencyManifest.Module> twitterModules = twitter.modules();
        Assert.assertEquals(twitterModules.size(), 1);
        Assert.assertEquals(twitterModules.get(0).org(), "wso2");
        Assert.assertEquals(twitterModules.get(0).packageName(), "twitter");
        Assert.assertEquals(twitterModules.get(0).moduleName(), "twitter");

        DependencyManifest.Package github = dependencies.get(2);
        Assert.assertEquals(github.org().value(), "wso2");
        Assert.assertEquals(github.name().value(), "github");
        Assert.assertEquals(github.version().toString(), "1.2.3");
        Assert.assertEquals(github.scope(), "testOnly");
        Assert.assertTrue(github.isTransitive());
        Assert.assertEquals(github.dependencies().size(), 1);
        Assert.assertEquals(github.modules().size(), 0);

        List<DependencyManifest.Tool> tools = new ArrayList<>(depsManifest.tools());
        Assert.assertEquals(tools.size(), 2);

        DependencyManifest.Tool openapiTool = tools.get(0);
        Assert.assertEquals(openapiTool.id().value(), "openapi");
        Assert.assertEquals(openapiTool.org().value(), "ballerina");
        Assert.assertEquals(openapiTool.name().value(), "openapi_tool");
        Assert.assertEquals(openapiTool.version().toString(), "0.2.0");

        DependencyManifest.Tool persistTool = tools.get(1);
        Assert.assertEquals(persistTool.id().value(), "persist");
        Assert.assertEquals(persistTool.org().value(), "ballerinax");
        Assert.assertEquals(persistTool.name().value(), "persist_tool");
        Assert.assertEquals(persistTool.version().toString(), "1.2.0");
    }

    @Test(description = "Test dependencies.toml without distribution version")
    public void testDependenciesTomlWithoutDistributionVersion() throws IOException {
        DependencyManifest depsManifest = getDependencyManifest(
                DEPENDENCIES_TOML_REPO.resolve("dependency-wo-dist-version.toml"));
        DiagnosticResult diagnostics = depsManifest.diagnostics();
        diagnostics.errors().forEach(OUT::println);
        Assert.assertFalse(diagnostics.hasErrors());
    }

    /**
     * Invalid Dependencies.toml file with dependencies missing org.
     * <p>
     * [[dependency]]
     * name = "foo1"
     * version = "0.1.0"
     */
    @Test
    public void testInvalidDependenciesTomlWithoutOrg() throws IOException {
        DependencyManifest depsManifest = getDependencyManifest(
                DEPENDENCIES_TOML_REPO.resolve("dependency-wo-org.toml"));
        DiagnosticResult diagnostics = depsManifest.diagnostics();
        diagnostics.errors().forEach(OUT::println);
        Assert.assertEquals(diagnostics.diagnostics().size(), 1);
        Iterator<Diagnostic> iterator = diagnostics.diagnostics().iterator();
        Diagnostic firstDiagnostic = iterator.next();
        Assert.assertEquals(firstDiagnostic.message(), "Detected corrupted Dependencies.toml file. " +
                "Dependencies will be updated to the latest versions.");
        Assert.assertEquals(firstDiagnostic.location().lineRange().toString(), "(5:0,26:17)");
    }

    /**
     * Invalid Dependencies.toml file with dependencies missing org value.
     * <p>
     * [[dependency]]
     * org =
     * name = "foo1"
     * version = "0.1.0"
     */
    @Test
    public void testInvalidDependenciesTomlWithoutOrgValue() throws IOException {
        DependencyManifest depsManifest = getDependencyManifest(
                DEPENDENCIES_TOML_REPO.resolve("dependency-wo-org-value.toml"));
        DiagnosticResult diagnostics = depsManifest.diagnostics();
        diagnostics.diagnostics().forEach(OUT::println);
        Assert.assertEquals(diagnostics.diagnostics().size(), 1);
        Iterator<Diagnostic> iterator = diagnostics.diagnostics().iterator();
        Diagnostic firstDiagnostic = iterator.next();
        Assert.assertEquals(firstDiagnostic.message(), "Detected corrupted Dependencies.toml file. " +
                "Dependencies will be updated to the latest versions.");
        Assert.assertEquals(firstDiagnostic.location().lineRange().toString(), "(5:0,28:17)");
    }

    /**
     * Invalid Dependencies.toml file with invalid org, name and version.
     * <p>
     * [[dependency]]
     * org = "foo-1"
     * name = "proj-1"
     * version = "221"
     */
    @Test
    public void testDependenciesTomlWithInvalidOrgNameVersion() throws IOException {
        DependencyManifest depsManifest = getDependencyManifest(
                DEPENDENCIES_TOML_REPO.resolve("invalid-org-name-value.toml"));
        DiagnosticResult diagnostics = depsManifest.diagnostics();
        Assert.assertEquals(diagnostics.diagnostics().size(), 1);
        Iterator<Diagnostic> iterator = diagnostics.diagnostics().iterator();
        Diagnostic firstDiagnostic = iterator.next();
        Assert.assertEquals(firstDiagnostic.message(), "Detected corrupted Dependencies.toml file. " +
                "Dependencies will be updated to the latest versions.");
        Assert.assertEquals(firstDiagnostic.location().lineRange().toString(), "(5:0,33:17)");
    }

    @Test(description = "Test dependencies.toml with invalid distribution version")
    public void testDependenciesTomlWithInvalidDistributionVersion() throws IOException {
        DependencyManifest depsManifest = getDependencyManifest(
                DEPENDENCIES_TOML_REPO.resolve("invalid-dist-version-value.toml"));
        DiagnosticResult diagnostics = depsManifest.diagnostics();
        Assert.assertEquals(diagnostics.diagnostics().size(), 1);
        Iterator<Diagnostic> iterator = diagnostics.diagnostics().iterator();
        Diagnostic firstDiagnostic = iterator.next();
        Assert.assertEquals(firstDiagnostic.message(), "Detected corrupted Dependencies.toml file. " +
                "Dependencies will be updated to the latest versions.");
    }

    @Test
    public void testEmptyDependenciesToml() throws IOException {
        DependencyManifest depsManifest = getDependencyManifest(
                DEPENDENCIES_TOML_REPO.resolve("dependencies-empty.toml"));
        depsManifest.diagnostics().diagnostics().forEach(OUT::println);
        Assert.assertFalse(depsManifest.diagnostics().hasErrors());
        // No warnings should be added
        Assert.assertEquals(depsManifest.diagnostics().diagnostics().size(), 0);
        List<DependencyManifest.Package> dependencies = new ArrayList<>(depsManifest.packages());
        Assert.assertEquals(dependencies.size(), 0);
    }

    @Test
    public void testDependenciesTomlOnlyHasComments() throws IOException {
        DependencyManifest depsManifest = getDependencyManifest(
                DEPENDENCIES_TOML_REPO.resolve("only-comments.toml"));
        depsManifest.diagnostics().diagnostics().forEach(OUT::println);
        Assert.assertFalse(depsManifest.diagnostics().hasErrors());
        // No warnings should be added
        Assert.assertEquals(depsManifest.diagnostics().diagnostics().size(), 0);
        List<DependencyManifest.Package> dependencies = new ArrayList<>(depsManifest.packages());
        Assert.assertEquals(dependencies.size(), 0);
    }

    @Test
    public void testInvalidDependenciesToml() throws IOException {
        DependencyManifest depsManifest = getDependencyManifest(
                DEPENDENCIES_TOML_REPO.resolve("dependencies-non-array.toml"));
        depsManifest.diagnostics().errors().forEach(OUT::println);
        Assert.assertEquals(depsManifest.diagnostics().diagnostics().size(), 1);
        Iterator<Diagnostic> iterator = depsManifest.diagnostics().diagnostics().iterator();
        Diagnostic firstDiagnostic = iterator.next();
        Assert.assertEquals(firstDiagnostic.message(), "Detected corrupted Dependencies.toml file. " +
                "Dependencies will be updated to the latest versions.");
    }

    @Test
    public void testDependenciesTomlWithInvalidDependencyScope() throws IOException {
        DependencyManifest depsManifest = getDependencyManifest(
                DEPENDENCIES_TOML_REPO.resolve("dependencies-invalid-scope.toml"));
        Assert.assertEquals(depsManifest.diagnostics().diagnostics().size(), 1);
        Iterator<Diagnostic> iterator = depsManifest.diagnostics().diagnostics().iterator();
        Diagnostic firstDiagnostic = iterator.next();
        Assert.assertEquals(firstDiagnostic.message(), "Detected corrupted Dependencies.toml file. " +
                "Dependencies will be updated to the latest versions.");
    }

    @Test
    public void testDependenciesTomlWithoutDepsTomlVersion() throws IOException {
        DependencyManifest depsManifest = getDependencyManifest(
                DEPENDENCIES_TOML_REPO.resolve("without-deps-toml-version.toml"));
        depsManifest.diagnostics().diagnostics().forEach(OUT::println);
        Assert.assertEquals(depsManifest.diagnostics().diagnostics().size(), 1);
        Iterator<Diagnostic> iterator = depsManifest.diagnostics().diagnostics().iterator();
        Diagnostic firstDiagnostic = iterator.next();
        Assert.assertEquals(firstDiagnostic.message(), "Detected corrupted Dependencies.toml file. " +
                "Dependencies will be updated to the latest versions.");
    }

    @Test
    public void testValidOldVersionDependenciesToml() throws IOException {
        DependencyManifest depsManifest = getDependencyManifest(
                DEPENDENCIES_TOML_REPO.resolve("old-dependencies-valid.toml"));
        depsManifest.diagnostics().diagnostics().forEach(OUT::println);
        Assert.assertFalse(depsManifest.diagnostics().hasErrors());
        Assert.assertEquals(depsManifest.diagnostics().warnings().size(), 1);
        Assert.assertEquals(depsManifest.diagnostics().warnings().iterator().next().message(),
                            "Detected an old version of Dependencies.toml file. This will be updated to v2 format.");

    }

    @Test
    public void testValidOldVersionDependenciesTomlHasLocalPackages() throws IOException {
        DependencyManifest depsManifest = getDependencyManifest(
                DEPENDENCIES_TOML_REPO.resolve("old-dependencies-with-local-packages.toml"));
        depsManifest.diagnostics().diagnostics().forEach(OUT::println);
        Assert.assertFalse(depsManifest.diagnostics().hasErrors());
        Assert.assertEquals(depsManifest.diagnostics().warnings().size(), 3);

        Iterator<Diagnostic> iterator = depsManifest.diagnostics().warnings().iterator();
        Assert.assertEquals(iterator.next().message(),
                            "Detected an old version of Dependencies.toml file. This will be updated to v2 format.");
        String localDepsWarning = "Detected local dependency declarations in Dependencies.toml file. "
                + "Add them to Ballerina.toml using following syntax:\n"
                + "[[dependency]]\n"
                + "org = \"wso2\"\n"
                + "name = \"locally\"\n"
                + "version = \"1.2.3\"\n"
                + "repository = \"local\"\n";
        Assert.assertEquals(iterator.next().message(), localDepsWarning);
    }

    @Test
    public void testDependenciesTomlWithoutRootPackage() throws IOException {
        DependencyManifest depsManifest = getDependencyManifest(
                DEPENDENCIES_TOML_REPO.resolve("dependencies-wo-root-pkg.toml"));
        depsManifest.diagnostics().diagnostics().forEach(OUT::println);
        Assert.assertFalse(depsManifest.diagnostics().hasErrors());
        Assert.assertEquals(depsManifest.diagnostics().warnings().size(), 1);
        Assert.assertEquals(depsManifest.diagnostics().warnings().iterator().next().message(),
                        "Detected corrupted Dependencies.toml file. This will be updated to latest dependencies.");
    }

    @Test(description = "Invalid Dependencies.toml file with additional attribute in modules array")
    public void testDependenciesTomlWithAdditionalAttributeInModulesArray() throws IOException {
        DependencyManifest depsManifest = getDependencyManifest(
                DEPENDENCIES_TOML_REPO.resolve("dependencies-with-additional-attribute-in-modules.toml"));
        DiagnosticResult diagnostics = depsManifest.diagnostics();
        Assert.assertEquals(diagnostics.diagnostics().size(), 1);
        Iterator<Diagnostic> iterator = diagnostics.diagnostics().iterator();
        Diagnostic firstDiagnostic = iterator.next();
        Assert.assertEquals(firstDiagnostic.message(), "Detected corrupted Dependencies.toml file. " +
                "Dependencies will be updated to the latest versions.");
        Assert.assertEquals(firstDiagnostic.location().lineRange().toString(), "(5:0,41:1)");
    }

    @Test(description = "Invalid Dependencies.toml file with additional attribute in dependencies array")
    public void testDependenciesTomlWithAdditionalAttributeInDependenciesArray() throws IOException {
        DependencyManifest depsManifest = getDependencyManifest(
                DEPENDENCIES_TOML_REPO.resolve("dependencies-with-additional-attribute-in-dependencies.toml"));
        DiagnosticResult diagnostics = depsManifest.diagnostics();
        Assert.assertEquals(diagnostics.diagnostics().size(), 1);
        Iterator<Diagnostic> iterator = diagnostics.diagnostics().iterator();
        Diagnostic firstDiagnostic = iterator.next();
        Assert.assertEquals(firstDiagnostic.message(), "Detected corrupted Dependencies.toml file. " +
                "Dependencies will be updated to the latest versions.");
        Assert.assertEquals(firstDiagnostic.location().lineRange().toString(), "(5:0,41:1)");
    }

    @Test(description = "Invalid Dependencies.toml file with invalid tool ids")
    public void testDependenciesTomlWithInvalidToolId() throws IOException {
        DependencyManifest depsManifest = getDependencyManifest(
                DEPENDENCIES_TOML_REPO.resolve("tool-with-invalid-id-value.toml"));
        DiagnosticResult diagnostics = depsManifest.diagnostics();
        Assert.assertEquals(diagnostics.diagnostics().size(), 1);
        Iterator<Diagnostic> iterator = diagnostics.diagnostics().iterator();
        Diagnostic firstDiagnostic = iterator.next();
        Assert.assertEquals(firstDiagnostic.message(), "Detected corrupted Dependencies.toml file. " +
                "Dependencies will be updated to the latest versions.");
        Assert.assertEquals(firstDiagnostic.location().lineRange().toString(), "(5:0,53:17)");
    }

    @Test(description = "Invalid Dependencies.toml file with invalid org, name in tool array")
    public void testDependenciesTomlWithInvalidToolOrgNameVersion() throws IOException {
        DependencyManifest depsManifest = getDependencyManifest(
                DEPENDENCIES_TOML_REPO.resolve("tool-with-invalid-org-name-version-value.toml"));
        DiagnosticResult diagnostics = depsManifest.diagnostics();
        Assert.assertEquals(diagnostics.diagnostics().size(), 1);
        Iterator<Diagnostic> iterator = diagnostics.diagnostics().iterator();
        Diagnostic firstDiagnostic = iterator.next();
        Assert.assertEquals(firstDiagnostic.message(), "Detected corrupted Dependencies.toml file. " +
                "Dependencies will be updated to the latest versions.");
        Assert.assertEquals(firstDiagnostic.location().lineRange().toString(), "(5:0,28:16)");
    }

    @Test(description = "Invalid Dependencies.toml file with missing org field in tool array")
    public void testInvalidDependenciesTomlToolWithoutOrg() throws IOException {
        DependencyManifest depsManifest = getDependencyManifest(
                DEPENDENCIES_TOML_REPO.resolve("tool-wo-org.toml"));
        DiagnosticResult diagnostics = depsManifest.diagnostics();
        diagnostics.errors().forEach(OUT::println);
        Assert.assertEquals(diagnostics.diagnostics().size(), 1);
        Iterator<Diagnostic> iterator = diagnostics.diagnostics().iterator();
        Diagnostic firstDiagnostic = iterator.next();
        Assert.assertEquals(firstDiagnostic.message(), "Detected corrupted Dependencies.toml file. " +
                "Dependencies will be updated to the latest versions.");
        Assert.assertEquals(firstDiagnostic.location().lineRange().toString(), "(5:0,28:17)");
    }

    @Test(description = "Invalid Dependencies.toml file with missing org value in tool array")
    public void testInvalidDependenciesTomlWithoutToolOrgValue() throws IOException {
        DependencyManifest depsManifest = getDependencyManifest(
                DEPENDENCIES_TOML_REPO.resolve("tool-wo-org-value.toml"));
        DiagnosticResult diagnostics = depsManifest.diagnostics();
        diagnostics.errors().forEach(OUT::println);
        Assert.assertEquals(diagnostics.diagnostics().size(), 1);
        Iterator<Diagnostic> iterator = diagnostics.diagnostics().iterator();
        Diagnostic firstDiagnostic = iterator.next();
        Assert.assertEquals(firstDiagnostic.message(), "Detected corrupted Dependencies.toml file. " +
                "Dependencies will be updated to the latest versions.");
        Assert.assertEquals(firstDiagnostic.location().lineRange().toString(), "(5:0,29:17)");
    }

    private DependencyManifest getDependencyManifest(Path dependenciesTomlPath) throws IOException {
        String dependenciesTomlContent = Files.readString(dependenciesTomlPath);
        TomlDocument dependenciesToml = TomlDocument.from(ProjectConstants.DEPENDENCIES_TOML, dependenciesTomlContent);
        PackageManifest packageManifest = getPackageManifest(DEPENDENCIES_TOML_REPO.resolve("ballerina.toml"));
        return DependencyManifestBuilder.from(dependenciesToml, packageManifest.descriptor()).dependencyManifest();
    }
}
