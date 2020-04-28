/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.instantexecution.inputs.undeclared

import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer
import org.gradle.integtests.fixtures.executer.AbstractGradleExecuter
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.internal.ToolingApiGradleExecutor

@LeaksFileHandles
class UndeclaredBuildInputsTestKitInjectedJavaPluginIntegrationTest extends AbstractUndeclaredBuildInputsIntegrationTest implements JavaPluginImplementation {
    TestFile jar
    TestFile testKitDir

    @Override
    GradleExecuter createExecuter() {
        testKitDir = file("test-kit")
        def executer = new TestKitBackedGradleExecuter(distribution, temporaryFolder, getBuildContext(), testKitDir)
        jar = file("plugins/sneaky.jar")
        executer.pluginClasspath.add(jar)
        return executer
    }

    def cleanup() {
        DaemonLogsAnalyzer.newAnalyzer(testKitDir.file(ToolingApiGradleExecutor.TEST_KIT_DAEMON_DIR_NAME)).killAll()
    }

    @Override
    void buildLogicApplication() {
        def builder = artifactBuilder()
        javaPlugin(builder.sourceFile("SneakyPlugin.java"))
        builder.resourceFile("META-INF/gradle-plugins/sneaky.properties") << """
implementation-class: SneakyPlugin
        """
        builder.buildJar(jar)
        buildFile << """
            plugins { id("sneaky") }
        """
    }

    static class TestKitBackedGradleExecuter extends AbstractGradleExecuter {
        List<File> pluginClasspath = []
        private final IntegrationTestBuildContext buildContext
        private final TestFile testKitDir

        TestKitBackedGradleExecuter(GradleDistribution distribution, TestDirectoryProvider testDirectoryProvider, IntegrationTestBuildContext buildContext, TestFile testKitDir) {
            super(distribution, testDirectoryProvider)
            this.testKitDir = testKitDir
            this.buildContext = buildContext
        }

        @Override
        void assertCanExecute() throws AssertionError {
        }

        @Override
        protected ExecutionResult doRun() {
            def runner = GradleRunner.create()
            runner.withGradleInstallation(buildContext.gradleHomeDir)
            runner.withTestKitDir(testKitDir)
            runner.withProjectDir(workingDir)
            def args = allArgs
            args.remove("--no-daemon")
            runner.withArguments(args)
            runner.withPluginClasspath(pluginClasspath)
            runner.forwardOutput()
            def runnerResult = runner.build()
            return OutputScrapingExecutionResult.from(runnerResult.output, "")
        }

        @Override
        protected ExecutionFailure doRunWithFailure() {
            throw new UnsupportedOperationException()
        }
    }
}
