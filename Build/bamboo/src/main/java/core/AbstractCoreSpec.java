package core;

/*
 * This file is part of the TYPO3 CMS project.
 *
 * It is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License, either version 2
 * of the License, or any later version.
 *
 * For the full copyright and license information, please read the
 * LICENSE.txt file that was distributed with this source code.
 *
 * The TYPO3 project - inspiring people to share!
 */

import java.util.ArrayList;

import com.atlassian.bamboo.specs.api.builders.BambooKey;
import com.atlassian.bamboo.specs.api.builders.permission.PermissionType;
import com.atlassian.bamboo.specs.api.builders.permission.Permissions;
import com.atlassian.bamboo.specs.api.builders.permission.PlanPermissions;
import com.atlassian.bamboo.specs.api.builders.plan.Job;
import com.atlassian.bamboo.specs.api.builders.plan.PlanIdentifier;
import com.atlassian.bamboo.specs.api.builders.plan.artifact.Artifact;
import com.atlassian.bamboo.specs.api.builders.plan.configuration.AllOtherPluginsConfiguration;
import com.atlassian.bamboo.specs.api.builders.plan.configuration.PluginConfiguration;
import com.atlassian.bamboo.specs.api.builders.requirement.Requirement;
import com.atlassian.bamboo.specs.api.builders.task.Task;
import com.atlassian.bamboo.specs.builders.task.CheckoutItem;
import com.atlassian.bamboo.specs.builders.task.CommandTask;
import com.atlassian.bamboo.specs.builders.task.NpmTask;
import com.atlassian.bamboo.specs.builders.task.ScriptTask;
import com.atlassian.bamboo.specs.builders.task.TestParserTask;
import com.atlassian.bamboo.specs.builders.task.VcsCheckoutTask;
import com.atlassian.bamboo.specs.model.task.ScriptTaskProperties;
import com.atlassian.bamboo.specs.model.task.TestParserTaskProperties;
import com.atlassian.bamboo.specs.util.MapBuilder;

/**
 * Abstract class with common methods of pre-merge and nightly plan
 */
abstract public class AbstractCoreSpec {

    protected static String bambooServerName = "https://bamboo.typo3.com:443";
    protected static String projectName = "TYPO3 Core";
    protected static String projectKey = "CORE";

    protected String composerRootVersionEnvironment = "COMPOSER_ROOT_VERSION=9.4.0@dev";

    protected String testingFrameworkBuildPath = "vendor/typo3/testing-framework/Resources/Core/Build/";

    /**
     * @todo This can be removed if acceptance mysql tests are rewritten and active again
     */
    protected String credentialsMysql =
        "typo3DatabaseName=\"func_test\"" +
        " typo3DatabaseUsername=\"root\"" +
        " typo3DatabasePassword=\"funcp\"" +
        " typo3DatabaseHost=\"mariadb10\"";

    /**
     * @todo This can be removed if acceptance mssql functional tests work again
     */
    protected String credentialsMssql =
        "typo3DatabaseDriver=\"sqlsrv\"" +
        " typo3DatabaseName=\"func\"" +
        " typo3DatabasePassword='Test1234!'" +
        " typo3DatabaseUsername=\"SA\"" +
        " typo3DatabaseHost=\"localhost\"" +
        " typo3DatabasePort=\"1433\"" +
        " typo3DatabaseCharset=\"utf-8\"";

    /**
     * Default permissions on core plans
     *
     * @param projectName
     * @param planName
     * @return
     */
    protected PlanPermissions getDefaultPlanPermissions(String projectKey, String planKey) {
        return new PlanPermissions(new PlanIdentifier(projectKey, planKey))
            .permissions(new Permissions()
            .groupPermissions("TYPO3 GmbH", PermissionType.ADMIN, PermissionType.VIEW, PermissionType.EDIT, PermissionType.BUILD, PermissionType.CLONE)
            .groupPermissions("TYPO3 Core Team", PermissionType.VIEW, PermissionType.BUILD)
            .loggedInUserPermissions(PermissionType.VIEW)
            .anonymousUserPermissionView()
        );
    }

    /**
     * Default plan plugin configuration
     *
     * @return
     */
    protected PluginConfiguration getDefaultPlanPluginConfiguration() {
        return new AllOtherPluginsConfiguration()
            .configuration(new MapBuilder()
            .put("custom", new MapBuilder()
                .put("artifactHandlers.useCustomArtifactHandlers", "false")
                .put("buildExpiryConfig", new MapBuilder()
                    .put("duration", "30")
                    .put("period", "days")
                    .put("labelsToKeep", "")
                    .put("expiryTypeResult", "true")
                    .put("buildsToKeep", "")
                    .put("enabled", "true")
                    .build()
                )
                .build()
            )
            .build()
        );
    }

    /**
     * Default job plugin configuration
     *
     * @return
     */
    protected PluginConfiguration getDefaultJobPluginConfiguration() {
        return new AllOtherPluginsConfiguration()
            .configuration(new MapBuilder()
                .put("repositoryDefiningWorkingDirectory", -1)
                .put("custom", new MapBuilder()
                    .put("auto", new MapBuilder()
                        .put("regex", "")
                        .put("label", "")
                        .build()
                    )
                    .put("buildHangingConfig.enabled", "false")
                    .put("ncover.path", "")
                    .put("clover", new MapBuilder()
                        .put("path", "")
                        .put("license", "")
                        .put("useLocalLicenseKey", "true")
                        .build()
                    )
                    .build()
                )
                .build()
            );
    }

    /**
     * Job composer validate
     *
     * @param String requirementIdentifier
     */
    protected Job getJobComposerValidate(String requirementIdentifier) {
        return new Job("Validate composer.json", new BambooKey("VC"))
        .description("Validate composer.json before actual tests are executed")
        .pluginConfigurations(this.getDefaultJobPluginConfiguration())
        .tasks(
            this.getTaskGitCloneRepository(),
            this.getTaskGitCherryPick(),
            new ScriptTask()
                .description("composer validate")
                .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
                .inlineBody(
                    this.getScriptTaskBashInlineBody() +
                    this.getScriptTaskComposer(requirementIdentifier) +
                    "composer validate"
                )
                .environmentVariables(this.composerRootVersionEnvironment)
        )
        .requirements(
            this.getRequirementDocker10()
        )
        .cleanWorkingDirectory(true);
    }

    /**
     * Job acceptance test installs system on mariadb
     *
     * @param String requirementIdentifier
     */
    protected Job getJobAcceptanceTestInstallMysql(String requirementIdentifier) {
        return new Job("Accept inst my " + requirementIdentifier, new BambooKey("ACINSTMY" + requirementIdentifier))
            .description("Install TYPO3 on mariadb and load introduction package " + requirementIdentifier)
            .pluginConfigurations(this.getDefaultJobPluginConfiguration())
            .tasks(
                this.getTaskGitCloneRepository(),
                this.getTaskGitCherryPick(),
                this.getTaskComposerInstall(requirementIdentifier),
                this.getTaskPrepareAcceptanceTest(),
                this.getTaskDockerDependenciesAcceptanceInstallMariadb10(),
                new ScriptTask()
                    .description("Install TYPO3 on mariadb 10")
                    .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
                    .inlineBody(
                        this.getScriptTaskBashInlineBody() +
                        "function codecept() {\n" +
                        "    docker run \\\n" +
                        "        -u ${HOST_UID} \\\n" +
                        "        -v /bamboo-data/${BAMBOO_COMPOSE_PROJECT_NAME}/passwd:/etc/passwd \\\n" +
                        "        -v ${BAMBOO_COMPOSE_PROJECT_NAME}_bamboo-data:/srv/bamboo/xml-data/build-dir/ \\\n" +
                        "        -e typo3InstallMysqlDatabaseHost=${typo3InstallMysqlDatabaseHost} \\\n" +
                        "        -e typo3InstallMysqlDatabaseName=${typo3InstallMysqlDatabaseName} \\\n" +
                        "        -e typo3InstallMysqlDatabaseUsername=${typo3InstallMysqlDatabaseUsername} \\\n" +
                        "        -e typo3InstallMysqlDatabasePassword=${typo3InstallMysqlDatabasePassword} \\\n" +
                        "        --name ${BAMBOO_COMPOSE_PROJECT_NAME}sib_adhoc \\\n" +
                        "        --network ${BAMBOO_COMPOSE_PROJECT_NAME}_test \\\n" +
                        "        --rm \\\n" +
                        "        typo3gmbh/" + requirementIdentifier.toLowerCase() + ":latest \\\n" +
                        "        bin/bash -c \"cd ${PWD}; ./bin/codecept $*\"\n" +
                        "}\n" +
                        "\n" +
                        "codecept run Install -d -c typo3/sysext/core/Tests/codeception.yml --env=mysql --xml reports.xml --html reports.html\n"
                    )
            )
            .finalTasks(
                this.getTaskStopDockerDependencies(),
                new TestParserTask(TestParserTaskProperties.TestType.JUNIT)
                    .resultDirectories("typo3temp/var/tests/AcceptanceReports/reports.xml")
            )
            .artifacts(new Artifact()
                .name("Test Report")
                .copyPattern("typo3temp/var/tests/AcceptanceReports/")
                .shared(false)
            )
            .requirements(
                this.getRequirementDocker10()
            )
            .cleanWorkingDirectory(true);
    }

    /**
     * Job acceptance test installs system and introduction package on pgsql
     *
     * @param String requirementIdentifier
     */
    protected Job getJobAcceptanceTestInstallPgsql(String requirementIdentifier) {
        return new Job("Accept inst pg " + requirementIdentifier, new BambooKey("ACINSTPG" + requirementIdentifier))
        .description("Install TYPO3 on pgsql and load introduction package " + requirementIdentifier)
        .pluginConfigurations(this.getDefaultJobPluginConfiguration())
        .tasks(
            this.getTaskGitCloneRepository(),
            this.getTaskGitCherryPick(),
            this.getTaskComposerInstall(requirementIdentifier),
            this.getTaskPrepareAcceptanceTest(),
            this.getTaskDockerDependenciesAcceptanceInstallPostgres10(),
            new ScriptTask()
                .description("Install TYPO3 on postgresql 10")
                .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
                .inlineBody(
                    this.getScriptTaskBashInlineBody() +
                    "function codecept() {\n" +
                    "    docker run \\\n" +
                    "        -u ${HOST_UID} \\\n" +
                    "        -v /bamboo-data/${BAMBOO_COMPOSE_PROJECT_NAME}/passwd:/etc/passwd \\\n" +
                    "        -v ${BAMBOO_COMPOSE_PROJECT_NAME}_bamboo-data:/srv/bamboo/xml-data/build-dir/ \\\n" +
                    "        -e typo3InstallPostgresqlDatabaseHost=${typo3InstallPostgresqlDatabaseHost} \\\n" +
                    "        -e typo3InstallPostgresqlDatabaseName=${typo3InstallPostgresqlDatabaseName} \\\n" +
                    "        -e typo3InstallPostgresqlDatabaseUsername=${typo3InstallPostgresqlDatabaseUsername} \\\n" +
                    "        -e typo3InstallPostgresqlDatabasePassword=${typo3InstallPostgresqlDatabasePassword} \\\n" +
                    "        --name ${BAMBOO_COMPOSE_PROJECT_NAME}sib_adhoc \\\n" +
                    "        --network ${BAMBOO_COMPOSE_PROJECT_NAME}_test \\\n" +
                    "        --rm \\\n" +
                    "        typo3gmbh/" + requirementIdentifier.toLowerCase() + ":latest \\\n" +
                    "        bin/bash -c \"cd ${PWD}; ./bin/codecept $*\"\n" +
                    "}\n" +
                    "\n" +
                    "codecept run Install -d -c typo3/sysext/core/Tests/codeception.yml --env=postgresql --xml reports.xml --html reports.html\n"
                )
        )
        .finalTasks(
            this.getTaskStopDockerDependencies(),
            new TestParserTask(TestParserTaskProperties.TestType.JUNIT)
                .resultDirectories("typo3temp/var/tests/AcceptanceReports/reports.xml")
        )
        .artifacts(new Artifact()
            .name("Test Report")
            .copyPattern("typo3temp/var/tests/AcceptanceReports/")
            .shared(false)
        )
        .requirements(
            this.getRequirementDocker10()
        )
        .cleanWorkingDirectory(true);
    }

    /**
     * Job acceptance test installs system and introduction package on sqlite
     *
     * @param String requirementIdentifier
     */
    protected Job getJobAcceptanceTestInstallSqlite(String requirementIdentifier) {
        return new Job("Accept inst sq " + requirementIdentifier, new BambooKey("ACINSTSQ" + requirementIdentifier))
        .description("Install TYPO3 on sqlite and load introduction package " + requirementIdentifier)
        .pluginConfigurations(this.getDefaultJobPluginConfiguration())
        .tasks(
            this.getTaskGitCloneRepository(),
            this.getTaskGitCherryPick(),
            this.getTaskComposerInstall(requirementIdentifier),
            this.getTaskPrepareAcceptanceTest(),
            this.getTaskDockerDependenciesAcceptanceInstallSqlite(),
            new ScriptTask()
                .description("Install TYPO3 on sqlite")
                .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
                .inlineBody(
                    this.getScriptTaskBashInlineBody() +
                    "function codecept() {\n" +
                    "    docker run \\\n" +
                    "        -u ${HOST_UID} \\\n" +
                    "        -v /bamboo-data/${BAMBOO_COMPOSE_PROJECT_NAME}/passwd:/etc/passwd \\\n" +
                    "        -v ${BAMBOO_COMPOSE_PROJECT_NAME}_bamboo-data:/srv/bamboo/xml-data/build-dir/ \\\n" +
                    "        --name ${BAMBOO_COMPOSE_PROJECT_NAME}sib_adhoc \\\n" +
                    "        --network ${BAMBOO_COMPOSE_PROJECT_NAME}_test \\\n" +
                    "        --rm \\\n" +
                    "        typo3gmbh/" + requirementIdentifier.toLowerCase() + ":latest \\\n" +
                    "        bin/bash -c \"cd ${PWD}; ./bin/codecept $*\"\n" +
                    "}\n" +
                    "\n" +
                    "codecept run Install -d -c typo3/sysext/core/Tests/codeception.yml --env=sqlite --xml reports.xml --html reports.html\n"
                )
        )
        .finalTasks(
            this.getTaskStopDockerDependencies(),
            new TestParserTask(TestParserTaskProperties.TestType.JUNIT)
                .resultDirectories("typo3temp/var/tests/AcceptanceReports/reports.xml")
        )
        .artifacts(new Artifact()
            .name("Test Report")
            .copyPattern("typo3temp/var/tests/AcceptanceReports/")
            .shared(false)
        )
        .requirements(
            this.getRequirementDocker10()
        )
        .cleanWorkingDirectory(true);
    }

    /**
     * Jobs for mysql based acceptance tests
     *
     * @todo Currently disabled and broken
     *
     * @param int numberOfChunks
     * @param String requirementIdentifier
     */
    protected ArrayList<Job> getJobsAcceptanceTestsMysql(int numberOfChunks, String requirementIdentifier) {
        ArrayList<Job> jobs = new ArrayList<Job>();

        for (int i=1; i<=numberOfChunks; i++) {
            String formattedI = "" + i;
            if (i < 10) {
                formattedI = "0" + i;
            }
            jobs.add(new Job("Accept my " + requirementIdentifier + " " + formattedI, new BambooKey("ACMY" + requirementIdentifier + formattedI))
                .description("Run acceptance tests" + requirementIdentifier)
                .pluginConfigurations(this.getDefaultJobPluginConfiguration())
                .tasks(
                    this.getTaskGitCloneRepository(),
                    this.getTaskGitCherryPick(),
                    this.getTaskComposerInstall(requirementIdentifier),
                    this.getTaskPrepareAcceptanceTest(),
                    new ScriptTask()
                        .description("Split acceptance tests")
                        .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
                        .inlineBody(
                            this.getScriptTaskBashInlineBody() +
                            "./" + this.testingFrameworkBuildPath + "Scripts/splitAcceptanceTests.sh " + numberOfChunks + "\n"
                        ),
                    new CommandTask()
                        .description("Execute codeception acceptance suite group " + formattedI)
                        .executable("codecept")
                        .argument("run Acceptance -d -g AcceptanceTests-Job-" + i + " -c " + this.testingFrameworkBuildPath + "AcceptanceTests.yml --xml reports.xml --html reports.html")
                        .environmentVariables(this.credentialsMysql)
                )
                .finalTasks(
                    this.getTaskStopDockerDependencies(),
                    new TestParserTask(TestParserTaskProperties.TestType.JUNIT)
                        .resultDirectories("typo3temp/var/tests/AcceptanceReports/reports.xml")
                )
                .artifacts(new Artifact()
                    .name("Test Report")
                    .copyPattern("typo3temp/var/tests/AcceptanceReports/")
                    .shared(false)
                )
                .requirements(
                    this.getRequirementDocker10()
                )
                .cleanWorkingDirectory(true)
                .enabled(false)
            );
        }

        return jobs;
    }

    /**
     * Jobs for mysql based functional tests
     *
     * @param int numberOfChunks
     * @param String requirementIdentifier
     */
    protected ArrayList<Job> getJobsFunctionalTestsMysql(int numberOfChunks, String requirementIdentifier) {
        ArrayList<Job> jobs = new ArrayList<Job>();

        for (int i=1; i<=numberOfChunks; i++) {
            String formattedI = "" + i;
            if (i < 10) {
                formattedI = "0" + i;
            }
            jobs.add(new Job("Func mysql " + requirementIdentifier + " " + formattedI, new BambooKey("FMY" + requirementIdentifier + formattedI))
                .description("Run functional tests on mysql DB " + requirementIdentifier)
                .pluginConfigurations(this.getDefaultJobPluginConfiguration())
                .tasks(
                    this.getTaskGitCloneRepository(),
                    this.getTaskGitCherryPick(),
                    this.getTaskComposerInstall(requirementIdentifier),
                    this.getTaskDockerDependenciesFunctionalMariadb10(),
                    this.getTaskSplitFunctionalJobs(numberOfChunks, requirementIdentifier),
                    new ScriptTask()
                        .description("Run phpunit with functional chunk " + formattedI)
                        .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
                        .inlineBody(
                            this.getScriptTaskBashInlineBody() +
                            "function phpunit() {\n" +
                            "    docker run \\\n" +
                            "        -u ${HOST_UID} \\\n" +
                            "        -v /bamboo-data/${BAMBOO_COMPOSE_PROJECT_NAME}/passwd:/etc/passwd \\\n" +
                            "        -v ${BAMBOO_COMPOSE_PROJECT_NAME}_bamboo-data:/srv/bamboo/xml-data/build-dir/ \\\n" +
                            "        -e typo3DatabaseName=func_test \\\n" +
                            "        -e typo3DatabaseUsername=root \\\n" +
                            "        -e typo3DatabasePassword=funcp \\\n" +
                            "        -e typo3DatabaseHost=mariadb10 \\\n" +
                            "        -e typo3TestingRedisHost=${BAMBOO_COMPOSE_PROJECT_NAME}sib_redis4_1 \\\n" +
                            "        -e typo3TestingMemcachedHost=${BAMBOO_COMPOSE_PROJECT_NAME}sib_memcached1-5_1 \\\n" +
                            "        --name ${BAMBOO_COMPOSE_PROJECT_NAME}sib_adhoc \\\n" +
                            "        --network ${BAMBOO_COMPOSE_PROJECT_NAME}_test \\\n" +
                            "        --rm \\\n" +
                            "        typo3gmbh/" + requirementIdentifier.toLowerCase() + ":latest \\\n" +
                            "        bin/bash -c \"cd ${PWD}; ./bin/phpunit $*\"\n" +
                            "}\n" +
                            "\n" +
                            "phpunit --log-junit test-reports/phpunit.xml -c " + this.testingFrameworkBuildPath + "FunctionalTests-Job-" + i + ".xml"
                        )
                )
                .finalTasks(
                    this.getTaskStopDockerDependencies(),
                    new TestParserTask(TestParserTaskProperties.TestType.JUNIT)
                        .resultDirectories("test-reports/phpunit.xml")
                )
                .requirements(
                    this.getRequirementDocker10()
                )
                .cleanWorkingDirectory(true)
            );
        }

        return jobs;
    }

    /**
     * Jobs for mssql based functional tests
     *
     * @todo Currently disabled and broken
     *
     * @param int numberOfChunks
     * @param String requirementIdentifier
     */
    protected ArrayList<Job> getJobsFunctionalTestsMssql(int numberOfChunks, String requirementIdentifier) {
        ArrayList<Job> jobs = new ArrayList<Job>();

        for (int i=1; i<=numberOfChunks; i++) {
            String formattedI = "" + i;
            if (i < 10) {
                formattedI = "0" + i;
            }
            jobs.add(new Job("Func mssql " + requirementIdentifier + " " + formattedI, new BambooKey("FMS" + requirementIdentifier + formattedI))
                .description("Run functional tests on mysql DB " + requirementIdentifier)
                .pluginConfigurations(this.getDefaultJobPluginConfiguration())
                .tasks(
                    this.getTaskGitCloneRepository(),
                    this.getTaskGitCherryPick(),
                    this.getTaskComposerInstall(requirementIdentifier),
                    this.getTaskSplitFunctionalJobs(numberOfChunks, requirementIdentifier),
                    new ScriptTask()
                        .description("Run phpunit with functional chunk " + formattedI)
                        .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
                        .inlineBody(
                            this.getScriptTaskBashInlineBody() +
                            "./bin/phpunit --exclude-group not-mssql --log-junit test-reports/phpunit.xml -c " + this.testingFrameworkBuildPath + "FunctionalTests-Job-" + i + ".xml"
                        )
                        .environmentVariables(this.credentialsMssql)
                )
                .finalTasks(
                    this.getTaskStopDockerDependencies(),
                    new TestParserTask(TestParserTaskProperties.TestType.JUNIT)
                        .resultDirectories("test-reports/phpunit.xml")
                )
                .requirements(
                    this.getRequirementDocker10()
                )
                .cleanWorkingDirectory(true)
                .enabled(false)
            );
        }

        return jobs;
    }

    /**
     * Jobs for pgsql based functional tests
     *
     * @param int numberOfChunks
     * @param String requirementIdentifier
     */
    protected ArrayList<Job> getJobsFunctionalTestsPgsql(int numberOfChunks, String requirementIdentifier) {
        ArrayList<Job> jobs = new ArrayList<Job>();

        for (int i=1; i<=numberOfChunks; i++) {
            String formattedI = "" + i;
            if (i < 10) {
                formattedI = "0" + i;
            }
            jobs.add(new Job("Func pgsql " + requirementIdentifier + " " + formattedI, new BambooKey("FPG" + requirementIdentifier + formattedI))
                .description("Run functional tests on pgsql DB " + requirementIdentifier)
                .pluginConfigurations(this.getDefaultJobPluginConfiguration())
                .tasks(
                    this.getTaskGitCloneRepository(),
                    this.getTaskGitCherryPick(),
                    this.getTaskComposerInstall(requirementIdentifier),
                    this.getTaskDockerDependenciesFunctionalPostgres10(),
                    this.getTaskSplitFunctionalJobs(numberOfChunks, requirementIdentifier),
                    new ScriptTask()
                        .description("Run phpunit with functional chunk " + formattedI)
                        .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
                        .inlineBody(
                            this.getScriptTaskBashInlineBody() +
                            "function phpunit() {\n" +
                            "    docker run \\\n" +
                            "        -u ${HOST_UID} \\\n" +
                            "        -v /bamboo-data/${BAMBOO_COMPOSE_PROJECT_NAME}/passwd:/etc/passwd \\\n" +
                            "        -v ${BAMBOO_COMPOSE_PROJECT_NAME}_bamboo-data:/srv/bamboo/xml-data/build-dir/ \\\n" +
                            "        -e typo3DatabaseDriver=pdo_pgsql \\\n" +
                            "        -e typo3DatabaseName=bamboo \\\n" +
                            "        -e typo3DatabaseUsername=bamboo \\\n" +
                            "        -e typo3DatabaseHost=postgres10 \\\n" +
                            "        -e typo3DatabasePassword=funcp \\\n" +
                            "        -e typo3TestingRedisHost=redis4 \\\n" +
                            "        -e typo3TestingMemcachedHost=memcached1-5 \\\n" +
                            "        --name ${BAMBOO_COMPOSE_PROJECT_NAME}sib_adhoc \\\n" +
                            "        --network ${BAMBOO_COMPOSE_PROJECT_NAME}_test \\\n" +
                            "        --rm \\\n" +
                            "        typo3gmbh/" + requirementIdentifier.toLowerCase() + ":latest \\\n" +
                            "        bin/bash -c \"cd ${PWD}; ./bin/phpunit $*\"\n" +
                            "}\n" +
                            "\n" +
                            "phpunit --exclude-group not-postgres --log-junit test-reports/phpunit.xml -c " + this.testingFrameworkBuildPath + "FunctionalTests-Job-" + i + ".xml"
                        )
                )
                .finalTasks(
                    this.getTaskStopDockerDependencies(),
                    new TestParserTask(TestParserTaskProperties.TestType.JUNIT)
                        .resultDirectories("test-reports/phpunit.xml")
                )
                .requirements(
                    this.getRequirementDocker10()
                )
                .cleanWorkingDirectory(true)
            );
        }

        return jobs;
    }

    /**
     * Jobs for sqlite based functional tests
     *
     * @param int numberOfChunks
     * @param String requirementIdentifier
     */
    protected ArrayList<Job> getJobsFunctionalTestsSqlite(int numberOfChunks, String requirementIdentifier) {
        ArrayList<Job> jobs = new ArrayList<Job>();

        for (int i=1; i<=numberOfChunks; i++) {
            String formattedI = "" + i;
            if (i < 10) {
                formattedI = "0" + i;
            }
            jobs.add(new Job("Func sqlite " + requirementIdentifier + " " + formattedI, new BambooKey("FSL" + requirementIdentifier + formattedI))
                .description("Run functional tests on sqlite DB " + requirementIdentifier)
                .pluginConfigurations(this.getDefaultJobPluginConfiguration())
                .tasks(
                    this.getTaskGitCloneRepository(),
                    this.getTaskGitCherryPick(),
                    this.getTaskComposerInstall(requirementIdentifier),
                    this.getTaskSplitFunctionalJobs(numberOfChunks, requirementIdentifier),
                    this.getTaskDockerDependenciesFunctionalSqlite(),
                    new ScriptTask()
                        .description("Run phpunit with functional chunk " + formattedI)
                        .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
                        .inlineBody(
                            this.getScriptTaskBashInlineBody() +
                            "function phpunit() {\n" +
                            "    docker run \\\n" +
                            "        -u ${HOST_UID} \\\n" +
                            "        -v /bamboo-data/${BAMBOO_COMPOSE_PROJECT_NAME}/passwd:/etc/passwd \\\n" +
                            "        -v ${BAMBOO_COMPOSE_PROJECT_NAME}_bamboo-data:/srv/bamboo/xml-data/build-dir/ \\\n" +
                            "        -e typo3DatabaseDriver=pdo_sqlite \\\n" +
                            "        -e typo3TestingRedisHost=redis4 \\\n" +
                            "        -e typo3TestingMemcachedHost=memcached1-5 \\\n" +
                            "        --name ${BAMBOO_COMPOSE_PROJECT_NAME}sib_adhoc \\\n" +
                            "        --network ${BAMBOO_COMPOSE_PROJECT_NAME}_test \\\n" +
                            "        --rm \\\n" +
                            "        typo3gmbh/" + requirementIdentifier.toLowerCase() + ":latest \\\n" +
                            "        bin/bash -c \"cd ${PWD}; ./bin/phpunit $*\"\n" +
                            "}\n" +
                            "\n" +
                            "phpunit --exclude-group not-sqlite --log-junit test-reports/phpunit.xml -c " + this.testingFrameworkBuildPath + "FunctionalTests-Job-" + i + ".xml"
                        )
                )
                .finalTasks(
                    this.getTaskStopDockerDependencies(),
                    new TestParserTask(TestParserTaskProperties.TestType.JUNIT)
                        .resultDirectories("test-reports/phpunit.xml")
                )
                .requirements(
                    this.getRequirementDocker10()
                )
                .cleanWorkingDirectory(true)
            );
        }

        return jobs;
    }

    /**
     * Job with integration test checking for valid @xy annotations
     *
     * @param String requirementIdentifier
     */
    protected Job getJobIntegrationAnnotations(String requirementIdentifier) {
        return new Job("Integration annotations", new BambooKey("IANNO"))
            .description("Check docblock-annotations by executing Build/Scripts/annotationChecker.php script")
            .pluginConfigurations(this.getDefaultJobPluginConfiguration())
            .tasks(
                this.getTaskGitCloneRepository(),
                this.getTaskGitCherryPick(),
                this.getTaskComposerInstall(requirementIdentifier),
                new ScriptTask()
                    .description("Execute annotations check script")
                    .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
                    .inlineBody(
                        this.getScriptTaskBashInlineBody() +
                        "function annotationChecker() {\n" +
                        "    docker run \\\n" +
                        "        -u ${HOST_UID} \\\n" +
                        "        -v /bamboo-data/${BAMBOO_COMPOSE_PROJECT_NAME}/passwd:/etc/passwd \\\n" +
                        "        -v ${BAMBOO_COMPOSE_PROJECT_NAME}_bamboo-data:/srv/bamboo/xml-data/build-dir/ \\\n" +
                        "        --name ${BAMBOO_COMPOSE_PROJECT_NAME}sib_adhoc \\\n" +
                        "        --rm \\\n" +
                        "        typo3gmbh/" + requirementIdentifier.toLowerCase() + ":latest \\\n" +
                        "        bin/bash -c \"cd ${PWD}; ./Build/Scripts/annotationChecker.php $*\"\n" +
                        "}\n" +
                        "\n" +
                        "annotationChecker"
                    )
            )
            .requirements(
                this.getRequirementDocker10()
            )
            .cleanWorkingDirectory(true);
    }

    /**
     * Job with various smaller script tests
     *
     * @param String requirementIdentifier
     */
    protected Job getJobIntegrationVarious(String requirementIdentifier) {
        // Exception code checker, xlf, permissions, rst file check
        return new Job("Integration various", new BambooKey("CDECC"))
            .description("Checks duplicate exceptions, git submodules, xlf files, permissions, rst")
            .pluginConfigurations(this.getDefaultJobPluginConfiguration())
            .tasks(
                this.getTaskGitCloneRepository(),
                this.getTaskGitCherryPick(),
                this.getTaskComposerInstall(requirementIdentifier),
                new ScriptTask()
                    .description("Run duplicate exception code check script")
                    .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
                    .inlineBody(
                        this.getScriptTaskBashInlineBody() +
                        "./Build/Scripts/duplicateExceptionCodeCheck.sh\n"
                    ),
                new ScriptTask()
                    .description("Run git submodule status and verify there are none")
                    .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
                    .inlineBody(
                        this.getScriptTaskBashInlineBody() +
                        "if [[ `git submodule status 2>&1 | wc -l` -ne 0 ]]; then\n" +
                        "    echo \\\"Found a submodule definition in repository\\\";\n" +
                        "    exit 99;\n" +
                        "fi\n"
                    ),
                new ScriptTask()
                    .description("Run permission check script")
                    .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
                    .inlineBody(
                        this.getScriptTaskBashInlineBody() +
                        "./Build/Scripts/checkFilePermissions.sh\n"
                    ),
                new ScriptTask()
                    .description("Run xlf check")
                    .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
                    .inlineBody(
                        this.getScriptTaskBashInlineBody() +
                        "./Build/Scripts/xlfcheck.sh"
                    ),
                new ScriptTask()
                    .description("Run rst check")
                    .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
                    .inlineBody(
                        this.getScriptTaskBashInlineBody() +
                        "function validateRstFiles() {\n" +
                        "    docker run \\\n" +
                        "        -u ${HOST_UID} \\\n" +
                        "        -v /bamboo-data/${BAMBOO_COMPOSE_PROJECT_NAME}/passwd:/etc/passwd \\\n" +
                        "        -v ${BAMBOO_COMPOSE_PROJECT_NAME}_bamboo-data:/srv/bamboo/xml-data/build-dir/ \\\n" +
                        "        --name ${BAMBOO_COMPOSE_PROJECT_NAME}sib_adhoc \\\n" +
                        "        --rm \\\n" +
                        "        typo3gmbh/" + requirementIdentifier.toLowerCase() + ":latest \\\n" +
                        "        bin/bash -c \"cd ${PWD}; ./Build/Scripts/validateRstFiles.php $*\"\n" +
                        "}\n" +
                        "\n" +
                        "validateRstFiles"
                    ),
                new ScriptTask()
                    .description("Run path length check")
                    .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
                    .inlineBody(
                        this.getScriptTaskBashInlineBody() +
                        "./Build/Scripts/maxFilePathLength.sh"
                    ),
                new ScriptTask()
                    .description("Run extension scanner ReST file reference tester")
                    .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
                    .inlineBody(
                        this.getScriptTaskBashInlineBody() +
                        "function extensionScannerRstFileReferences() {\n" +
                        "    docker run \\\n" +
                        "        -u ${HOST_UID} \\\n" +
                        "        -v /bamboo-data/${BAMBOO_COMPOSE_PROJECT_NAME}/passwd:/etc/passwd \\\n" +
                        "        -v ${BAMBOO_COMPOSE_PROJECT_NAME}_bamboo-data:/srv/bamboo/xml-data/build-dir/ \\\n" +
                        "        --name ${BAMBOO_COMPOSE_PROJECT_NAME}sib_adhoc \\\n" +
                        "        --rm \\\n" +
                        "        typo3gmbh/" + requirementIdentifier.toLowerCase() + ":latest \\\n" +
                        "        bin/bash -c \"cd ${PWD}; ./Build/Scripts/extensionScannerRstFileReferences.php $*\"\n" +
                        "}\n" +
                        "\n" +
                        "extensionScannerRstFileReferences"
                    ),
                new ScriptTask()
                    .description("Run functional fixture csv format checker")
                    .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
                    .inlineBody(
                        this.getScriptTaskBashInlineBody() +
                        "function checkIntegrityCsvFixtures() {\n" +
                        "    docker run \\\n" +
                        "        -u ${HOST_UID} \\\n" +
                        "        -v /bamboo-data/${BAMBOO_COMPOSE_PROJECT_NAME}/passwd:/etc/passwd \\\n" +
                        "        -v ${BAMBOO_COMPOSE_PROJECT_NAME}_bamboo-data:/srv/bamboo/xml-data/build-dir/ \\\n" +
                        "        --name ${BAMBOO_COMPOSE_PROJECT_NAME}sib_adhoc \\\n" +
                        "        --rm \\\n" +
                        "        typo3gmbh/" + requirementIdentifier.toLowerCase() + ":latest \\\n" +
                        "        bin/bash -c \"cd ${PWD}; ./Build/Scripts/checkIntegrityCsvFixtures.php $*\"\n" +
                        "}\n" +
                        "\n" +
                        "checkIntegrityCsvFixtures"
                    ),
                new ScriptTask()
                    .description("Run composer.json integrity check")
                    .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
                    .inlineBody(
                        this.getScriptTaskBashInlineBody() +
                        "function checkIntegrityComposer() {\n" +
                        "    docker run \\\n" +
                        "        -u ${HOST_UID} \\\n" +
                        "        -v /bamboo-data/${BAMBOO_COMPOSE_PROJECT_NAME}/passwd:/etc/passwd \\\n" +
                        "        -v ${BAMBOO_COMPOSE_PROJECT_NAME}_bamboo-data:/srv/bamboo/xml-data/build-dir/ \\\n" +
                        "        --name ${BAMBOO_COMPOSE_PROJECT_NAME}sib_adhoc \\\n" +
                        "        --rm \\\n" +
                        "        typo3gmbh/" + requirementIdentifier.toLowerCase() + ":latest \\\n" +
                        "        bin/bash -c \"cd ${PWD}; ./Build/Scripts/checkIntegrityComposer.php $*\"\n" +
                        "}\n" +
                        "\n" +
                        "checkIntegrityComposer"
                    )
            )
            .requirements(
                this.getRequirementDocker10()
            )
            .cleanWorkingDirectory(true);
    }

    /**
     * Job for javascript unit tests
     *
     * @param String requirementIdentifier
     */
    protected Job getJobUnitJavaScript(String requirementIdentifier) {
        return new Job("Unit JavaScript", new BambooKey("JSUT"))
            .description("Run JavaScript unit tests")
            .pluginConfigurations(this.getDefaultJobPluginConfiguration())
            .tasks(
                this.getTaskGitCloneRepository(),
                this.getTaskGitCherryPick(),
                this.getTaskComposerInstall(requirementIdentifier),
                new ScriptTask()
                    .description("yarn install in Build/ dir")
                    .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
                    .inlineBody(
                        this.getScriptTaskBashInlineBody() +
                        "function yarn() {\n" +
                        "    docker run \\\n" +
                        "        -u ${HOST_UID} \\\n" +
                        "        -v /bamboo-data/${BAMBOO_COMPOSE_PROJECT_NAME}/passwd:/etc/passwd \\\n" +
                        "        -v ${BAMBOO_COMPOSE_PROJECT_NAME}_bamboo-data:/srv/bamboo/xml-data/build-dir/ \\\n" +
                        "        -e HOME=${HOME} \\\n" +
                        "        --name ${BAMBOO_COMPOSE_PROJECT_NAME}sib_adhoc \\\n" +
                        "        --rm \\\n" +
                        "        typo3gmbh/" + requirementIdentifier.toLowerCase() + ":latest \\\n" +
                        "        bin/bash -c \"cd ${PWD}/Build; yarn $*\"\n" +
                        "}\n" +
                        "\n" +
                        "yarn install"
                    ),
                new ScriptTask()
                    .description("Run tests")
                    .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
                    .inlineBody(
                        this.getScriptTaskBashInlineBody() +
                        "function karma() {\n" +
                        "    docker run \\\n" +
                        "        -u ${HOST_UID} \\\n" +
                        "        -v /bamboo-data/${BAMBOO_COMPOSE_PROJECT_NAME}/passwd:/etc/passwd \\\n" +
                        "        -v ${BAMBOO_COMPOSE_PROJECT_NAME}_bamboo-data:/srv/bamboo/xml-data/build-dir/ \\\n" +
                        "        -e HOME=${HOME} \\\n" +
                        "        --name ${BAMBOO_COMPOSE_PROJECT_NAME}sib_adhoc \\\n" +
                        "        --rm \\\n" +
                        "        typo3gmbh/" + requirementIdentifier.toLowerCase() + ":latest \\\n" +
                        "        bin/bash -c \"cd ${PWD}; ./Build/node_modules/karma/bin/karma $*\"\n" +
                        "}\n" +
                        "\n" +
                        "karma start " + this.testingFrameworkBuildPath + "Configuration/JSUnit/karma.conf.js --single-run"
                    )
            )
            .finalTasks(
                new TestParserTask(TestParserTaskProperties.TestType.JUNIT)
                    .resultDirectories("typo3temp/var/tests/*")
            )
            .artifacts(
                new Artifact()
                    .name("Clover Report (System)")
                    .copyPattern("**/*.*")
                    .location("Build/target/site/clover")
                    .shared(false)
            )
            .requirements(
                this.getRequirementDocker10()
            )
            .cleanWorkingDirectory(true);
    }

    /**
     * Job for PHP lint
     *
     * @param String requirementIdentifier
     */
    protected Job getJobLintPhp(String requirementIdentifier) {
        return new Job("Lint " + requirementIdentifier, new BambooKey("L" + requirementIdentifier))
            .description("Run php -l on source files for linting " + requirementIdentifier)
            .pluginConfigurations(this.getDefaultJobPluginConfiguration())
            .tasks(
                this.getTaskGitCloneRepository(),
                this.getTaskGitCherryPick(),
                new ScriptTask()
                    .description("Run php lint")
                    .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
                    .inlineBody(
                        this.getScriptTaskBashInlineBody() +
                        "function runLint() {\n" +
                        "    docker run \\\n" +
                        "        -u ${HOST_UID} \\\n" +
                        "        -v /bamboo-data/${BAMBOO_COMPOSE_PROJECT_NAME}/passwd:/etc/passwd \\\n" +
                        "        -v ${BAMBOO_COMPOSE_PROJECT_NAME}_bamboo-data:/srv/bamboo/xml-data/build-dir/ \\\n" +
                        "        -e HOME=${HOME} \\\n" +
                        "        --name ${BAMBOO_COMPOSE_PROJECT_NAME}sib_adhoc \\\n" +
                        "        --rm \\\n" +
                        "        typo3gmbh/" + requirementIdentifier.toLowerCase() + ":latest \\\n" +
                        "        bin/bash -c \"cd ${PWD}; find . -name \\*.php -print0 | xargs -0 -n1 -P2 php -n -c /etc/php/cli-no-xdebug/php.ini -l >/dev/null\"\n" +
                        "}\n" +
                        "\n" +
                        "runLint"
                    )
            )
            .requirements(
                this.getRequirementDocker10()
            )
            .cleanWorkingDirectory(true);
    }

    /**
     * Job for lint npm scss and typescript
     *
     * @param String requirementIdentifier
     */
    protected Job getJobLintScssTs(String requirementIdentifier) {
        return new Job("Lint scss ts", new BambooKey("LSTS"))
            .description("Run npm lint, run npm run build-js")
            .pluginConfigurations(this.getDefaultJobPluginConfiguration())
            .tasks(
                this.getTaskGitCloneRepository(),
                this.getTaskGitCherryPick(),
                new ScriptTask()
                    .description("yarn install in Build/ dir")
                    .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
                    .inlineBody(
                        this.getScriptTaskBashInlineBody() +
                        "function yarn() {\n" +
                        "    docker run \\\n" +
                        "        -u ${HOST_UID} \\\n" +
                        "        -v /bamboo-data/${BAMBOO_COMPOSE_PROJECT_NAME}/passwd:/etc/passwd \\\n" +
                        "        -v ${BAMBOO_COMPOSE_PROJECT_NAME}_bamboo-data:/srv/bamboo/xml-data/build-dir/ \\\n" +
                        "        -e HOME=${HOME} \\\n" +
                        "        --name ${BAMBOO_COMPOSE_PROJECT_NAME}sib_adhoc \\\n" +
                        "        --rm \\\n" +
                        "        typo3gmbh/" + requirementIdentifier.toLowerCase() + ":latest \\\n" +
                        "        bin/bash -c \"cd ${PWD}/Build; yarn $*\"\n" +
                        "}\n" +
                        "\n" +
                        "yarn install"
                    ),
                new ScriptTask()
                    .description("Run npm lint")
                    .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
                    .inlineBody(
                        this.getScriptTaskBashInlineBody() +
                        "function npm() {\n" +
                        "    docker run \\\n" +
                        "        -u ${HOST_UID} \\\n" +
                        "        -v /bamboo-data/${BAMBOO_COMPOSE_PROJECT_NAME}/passwd:/etc/passwd \\\n" +
                        "        -v ${BAMBOO_COMPOSE_PROJECT_NAME}_bamboo-data:/srv/bamboo/xml-data/build-dir/ \\\n" +
                        "        -e HOME=${HOME} \\\n" +
                        "        --name ${BAMBOO_COMPOSE_PROJECT_NAME}sib_adhoc \\\n" +
                        "        --rm \\\n" +
                        "        typo3gmbh/" + requirementIdentifier.toLowerCase() + ":latest \\\n" +
                        "        bin/bash -c \"cd ${PWD}/Build; npm $*\"\n" +
                        "}\n" +
                        "\n" +
                        "npm run lint"
                    ),
                new ScriptTask()
                    .description("Run npm build-js")
                    .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
                    .inlineBody(
                        this.getScriptTaskBashInlineBody() +
                        "function npm() {\n" +
                        "    docker run \\\n" +
                        "        -u ${HOST_UID} \\\n" +
                        "        -v /bamboo-data/${BAMBOO_COMPOSE_PROJECT_NAME}/passwd:/etc/passwd \\\n" +
                        "        -v ${BAMBOO_COMPOSE_PROJECT_NAME}_bamboo-data:/srv/bamboo/xml-data/build-dir/ \\\n" +
                        "        -e HOME=${HOME} \\\n" +
                        "        --name ${BAMBOO_COMPOSE_PROJECT_NAME}sib_adhoc \\\n" +
                        "        --rm \\\n" +
                        "        typo3gmbh/" + requirementIdentifier.toLowerCase() + ":latest \\\n" +
                        "        bin/bash -c \"cd ${PWD}/Build; npm $*\"\n" +
                        "}\n" +
                        "\n" +
                        "npm run build-js"
                    ),
                new ScriptTask()
                    .description("git status to check for changed files after build-js")
                    .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
                    .inlineBody(
                        this.getScriptTaskBashInlineBody() +
                        "git status | grep -q \"nothing to commit, working tree clean\""
                    )
            )
            .requirements(
                this.getRequirementDocker10()
            )
            .cleanWorkingDirectory(true);
    }

    /**
     * Job for unit testing PHP
     *
     * @param String requirementIdentifier
     */
    protected Job getJobUnitPhp(String requirementIdentifier) {
        return new Job("Unit " + requirementIdentifier, new BambooKey("UT" + requirementIdentifier))
            .description("Run unit tests " + requirementIdentifier)
            .pluginConfigurations(this.getDefaultJobPluginConfiguration())
            .tasks(
                this.getTaskGitCloneRepository(),
                this.getTaskGitCherryPick(),
                this.getTaskComposerInstall(requirementIdentifier),
                new ScriptTask()
                    .description("Run phpunit")
                    .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
                    .inlineBody(
                        this.getScriptTaskBashInlineBody() +
                        "function phpunit() {\n" +
                        "    docker run \\\n" +
                        "        -u ${HOST_UID} \\\n" +
                        "        -v /bamboo-data/${BAMBOO_COMPOSE_PROJECT_NAME}/passwd:/etc/passwd \\\n" +
                        "        -v ${BAMBOO_COMPOSE_PROJECT_NAME}_bamboo-data:/srv/bamboo/xml-data/build-dir/ \\\n" +
                        "        --name ${BAMBOO_COMPOSE_PROJECT_NAME}sib_adhoc \\\n" +
                        "        --network ${BAMBOO_COMPOSE_PROJECT_NAME}_test \\\n" +
                        "        --rm \\\n" +
                        "        typo3gmbh/" + requirementIdentifier.toLowerCase() + ":latest \\\n" +
                        "        bin/bash -c \"cd ${PWD}; php -n -c /etc/php/cli-no-xdebug/php.ini bin/phpunit $*\"\n" +
                        "}\n" +
                        "\n" +
                        "phpunit --log-junit test-reports/phpunit.xml -c " + this.testingFrameworkBuildPath + "UnitTests.xml"
                    )
            )
            .finalTasks(
                new TestParserTask(TestParserTaskProperties.TestType.JUNIT)
                    .resultDirectories("test-reports/phpunit.xml")
            )
            .requirements(
                this.getRequirementDocker10()
            )
            .cleanWorkingDirectory(true);
    }

    /**
     * Job for unit testing deprecated PHP
     *
     * @param String requirementIdentifier
     */
    protected Job getJobUnitDeprecatedPhp(String requirementIdentifier) {
        return new Job("Unit deprecated " + requirementIdentifier, new BambooKey("UTD" + requirementIdentifier))
            .description("Run deprecated unit tests " + requirementIdentifier)
            .pluginConfigurations(this.getDefaultJobPluginConfiguration())
            .tasks(
                this.getTaskGitCloneRepository(),
                this.getTaskGitCherryPick(),
                this.getTaskComposerInstall(requirementIdentifier),
                new ScriptTask()
                    .description("Run phpunit")
                    .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
                    .inlineBody(
                        this.getScriptTaskBashInlineBody() +
                        "function phpunit() {\n" +
                        "    docker run \\\n" +
                        "        -u ${HOST_UID} \\\n" +
                        "        -v /bamboo-data/${BAMBOO_COMPOSE_PROJECT_NAME}/passwd:/etc/passwd \\\n" +
                        "        -v ${BAMBOO_COMPOSE_PROJECT_NAME}_bamboo-data:/srv/bamboo/xml-data/build-dir/ \\\n" +
                        "        --name ${BAMBOO_COMPOSE_PROJECT_NAME}sib_adhoc \\\n" +
                        "        --network ${BAMBOO_COMPOSE_PROJECT_NAME}_test \\\n" +
                        "        --rm \\\n" +
                        "        typo3gmbh/" + requirementIdentifier.toLowerCase() + ":latest \\\n" +
                        "        bin/bash -c \"cd ${PWD}; php -n -c /etc/php/cli-no-xdebug/php.ini bin/phpunit $*\"\n" +
                        "}\n" +
                        "\n" +
                        "phpunit --log-junit test-reports/phpunit.xml -c " + this.testingFrameworkBuildPath + "UnitTestsDeprecated.xml"
                    )
            )
            .finalTasks(
                new TestParserTask(TestParserTaskProperties.TestType.JUNIT)
                    .resultDirectories("test-reports/phpunit.xml")
            )
            .requirements(
                this.getRequirementDocker10()
            )
            .cleanWorkingDirectory(true);
    }

    /**
     * Jobs for unit testing PHP in random test order
     *
     * @param int numberOfRuns
     * @param String requirementIdentifier
     */
    protected ArrayList<Job> getJobUnitPhpRandom(int numberOfRuns, String requirementIdentifier) {
        ArrayList<Job> jobs = new ArrayList<Job>();

        for (int i=1; i<=numberOfRuns; i++) {
            jobs.add(new Job("Unit " + requirementIdentifier + " random " + i, new BambooKey("UTR" + requirementIdentifier + i))
                .description("Run unit tests on " + requirementIdentifier + " in random order 0" + i)
                .pluginConfigurations(this.getDefaultJobPluginConfiguration())
                .tasks(
                    this.getTaskGitCloneRepository(),
                    this.getTaskGitCherryPick(),
                    this.getTaskComposerInstall(requirementIdentifier),
                    new ScriptTask()
                        .description("Run phpunit-randomizer")
                        .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
                        .inlineBody(
                            this.getScriptTaskBashInlineBody() +
                            "function phpunitRandomizer() {\n" +
                            "    docker run \\\n" +
                            "        -u ${HOST_UID} \\\n" +
                            "        -v /bamboo-data/${BAMBOO_COMPOSE_PROJECT_NAME}/passwd:/etc/passwd \\\n" +
                            "        -v ${BAMBOO_COMPOSE_PROJECT_NAME}_bamboo-data:/srv/bamboo/xml-data/build-dir/ \\\n" +
                            "        --name ${BAMBOO_COMPOSE_PROJECT_NAME}sib_adhoc \\\n" +
                            "        --network ${BAMBOO_COMPOSE_PROJECT_NAME}_test \\\n" +
                            "        --rm \\\n" +
                            "        typo3gmbh/" + requirementIdentifier.toLowerCase() + ":latest \\\n" +
                            "        bin/bash -c \"cd ${PWD}; php -n -c /etc/php/cli-no-xdebug/php.ini bin/phpunit-randomizer $*\"\n" +
                            "}\n" +
                            "\n" +
                            "phpunitRandomizer --log-junit test-reports/phpunit.xml -c " + this.testingFrameworkBuildPath + "UnitTests.xml --order rand"
                        )
                )
                .finalTasks(
                    new TestParserTask(TestParserTaskProperties.TestType.JUNIT)
                        .resultDirectories("test-reports/phpunit.xml")
                )
                .requirements(
                    this.getRequirementDocker10()
                )
                .cleanWorkingDirectory(true)
            );
        }

        return jobs;
    }

    /**
     * Task definition for basic core clone of linked default repository
     */
    protected Task getTaskGitCloneRepository() {
        return new VcsCheckoutTask()
            .description("Checkout git core")
            .checkoutItems(new CheckoutItem().defaultRepository());
    }

    /**
     * Task definition to cherry pick a patch set from gerrit on top of cloned core
     */
    protected Task getTaskGitCherryPick() {
        return new ScriptTask()
            .description("Gerrit cherry pick")
            .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
            .inlineBody(
                this.getScriptTaskBashInlineBody() +
                "CHANGEURL=${bamboo.changeUrl}\n" +
                "CHANGEURLID=${CHANGEURL#https://review.typo3.org/}\n" +
                "PATCHSET=${bamboo.patchset}\n" +
                "\n" +
                "if [[ $CHANGEURL ]]; then\n" +
                "    gerrit-cherry-pick https://review.typo3.org/Packages/TYPO3.CMS $CHANGEURLID/$PATCHSET || exit 1\n" +
                "fi\n"
            );
    }

    /**
     * Task definition to execute composer install
     *
     * @param String requirementIdentifier
     */
    protected Task getTaskComposerInstall(String requirementIdentifier) {
        return new ScriptTask()
            .description("composer install")
            .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
            .inlineBody(
                this.getScriptTaskBashInlineBody() +
                this.getScriptTaskComposer(requirementIdentifier) +
                "composer install -n"
            )
            .environmentVariables(this.composerRootVersionEnvironment);
    }

    /**
     * Task to prepare an acceptance test
     */
    protected Task getTaskPrepareAcceptanceTest() {
        return new ScriptTask()
            .description("Prepare acceptance test environment")
            .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
            .inlineBody(
                this.getScriptTaskBashInlineBody() +
                "mkdir -p typo3temp/var/tests/\n"
            );
    }

    /**
     * Start docker sibling containers to execute acceptance tests on mariadb
     */
    protected Task getTaskDockerDependenciesAcceptanceInstallMariadb10() {
        return new ScriptTask()
            .description("Start docker siblings for acceptance test install mariadb")
            .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
            .inlineBody(
                this.getScriptTaskBashInlineBody() +
                "cd Build/testing-docker/bamboo\n" +
                "echo COMPOSE_PROJECT_NAME=${BAMBOO_COMPOSE_PROJECT_NAME}sib > .env\n" +
                "docker-compose run start_dependencies_acceptance_install_mariadb10"
            );
    }

    /**
     * Start docker sibling containers to execute acceptance tests on postgres
     */
    protected Task getTaskDockerDependenciesAcceptanceInstallPostgres10() {
        return new ScriptTask()
            .description("Start docker siblings for acceptance test install postgres")
            .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
            .inlineBody(
                this.getScriptTaskBashInlineBody() +
                "cd Build/testing-docker/bamboo\n" +
                "echo COMPOSE_PROJECT_NAME=${BAMBOO_COMPOSE_PROJECT_NAME}sib > .env\n" +
                "docker-compose run start_dependencies_acceptance_install_postgres10"
            );
    }

    /**
     * Start docker sibling containers to execute acceptance tests on sqlite
     */
    protected Task getTaskDockerDependenciesAcceptanceInstallSqlite() {
        return new ScriptTask()
            .description("Start docker siblings for acceptance test install sqlite")
            .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
            .inlineBody(
                this.getScriptTaskBashInlineBody() +
                "cd Build/testing-docker/bamboo\n" +
                "echo COMPOSE_PROJECT_NAME=${BAMBOO_COMPOSE_PROJECT_NAME}sib > .env\n" +
                "docker-compose run start_dependencies_acceptance_install_sqlite"
            );
    }

    /**
     * Start docker sibling containers to execute functional tests on mariadb
     */
    protected Task getTaskDockerDependenciesFunctionalMariadb10() {
        return new ScriptTask()
            .description("Start docker siblings for functional tests on mariadb")
            .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
            .inlineBody(
                this.getScriptTaskBashInlineBody() +
                "cd Build/testing-docker/bamboo\n" +
                "echo COMPOSE_PROJECT_NAME=${BAMBOO_COMPOSE_PROJECT_NAME}sib > .env\n" +
                "docker-compose run start_dependencies_functional_mariadb10"
            );
    }

    /**
     * Start docker sibling containers to execute functional tests on postgres
     */
    protected Task getTaskDockerDependenciesFunctionalPostgres10() {
        return new ScriptTask()
            .description("Start docker siblings for functional tests on postgres10")
            .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
            .inlineBody(
                this.getScriptTaskBashInlineBody() +
                "cd Build/testing-docker/bamboo\n" +
                "echo COMPOSE_PROJECT_NAME=${BAMBOO_COMPOSE_PROJECT_NAME}sib > .env\n" +
                "docker-compose run start_dependencies_functional_postgres10"
            );
    }

    /**
     * Start docker sibling containers to execute functional tests on sqlite
     */
    protected Task getTaskDockerDependenciesFunctionalSqlite() {
        return new ScriptTask()
            .description("Start docker siblings for functional tests on sqlite")
            .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
            .inlineBody(
                this.getScriptTaskBashInlineBody() +
                "cd Build/testing-docker/bamboo\n" +
                "echo COMPOSE_PROJECT_NAME=${BAMBOO_COMPOSE_PROJECT_NAME}sib > .env\n" +
                "docker-compose run start_dependencies_functional_sqlite"
            );
    }

    /**
     * Stop started docker containers
     */
    protected Task getTaskStopDockerDependencies() {
        return new ScriptTask()
            .description("Stop docker siblings")
            .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
            .inlineBody(
                this.getScriptTaskBashInlineBody() +
                "cd Build/testing-docker/bamboo\n" +
                "docker-compose down -v"
            );
    }

    /**
     * Task to split functional jobs into chunks
     *
     * @param int numberOfJobs
     * @param String requirementIdentifier
     */
    protected Task getTaskSplitFunctionalJobs(int numberOfJobs, String requirementIdentifier) {
        return new ScriptTask()
            .description("Create list of test files to execute per job")
            .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
            .inlineBody(
                this.getScriptTaskBashInlineBody() +
                "function splitFunctionalTests() {\n" +
                "    docker run \\\n" +
                "        -u ${HOST_UID} \\\n" +
                "        -v /bamboo-data/${BAMBOO_COMPOSE_PROJECT_NAME}/passwd:/etc/passwd \\\n" +
                "        -v ${BAMBOO_COMPOSE_PROJECT_NAME}_bamboo-data:/srv/bamboo/xml-data/build-dir/ \\\n" +
                "        --name ${BAMBOO_COMPOSE_PROJECT_NAME}sib_adhoc \\\n" +
                "        --rm \\\n" +
                "        typo3gmbh/" + requirementIdentifier.toLowerCase() + ":latest \\\n" +
                "        bin/bash -c \"cd ${PWD}; ./" + this.testingFrameworkBuildPath + "Scripts/splitFunctionalTests.php $*\"\n" +
                "}\n" +
                "\n" +
                "splitFunctionalTests " + numberOfJobs + " -v"
            );
    }

    /**
     * Requirement for docker 1.0 set by bamboo-agents
     */
    protected Requirement getRequirementDocker10() {
        return new Requirement("system.hasDocker")
            .matchValue("1.0")
            .matchType(Requirement.MatchType.EQUALS);
    }

    /**
     * A bash header for script tasks forking a bash if needed
     */
    protected String getScriptTaskBashInlineBody() {
        return
            "#!/bin/bash\n" +
            "\n" +
            "if [ \"$(ps -p \"$$\" -o comm=)\" != \"bash\" ]; then\n" +
            "    bash \"$0\" \"$@\"\n" +
            "    exit \"$?\"\n" +
            "fi\n" +
            "\n" +
            "set -x\n" +
            "\n";
    }

    /**
     * A bash function aliasing 'composer' as docker command
     *
     * @param String requirementIdentifier
     */
    protected String getScriptTaskComposer(String requirementIdentifier) {
        return
            "function composer() {\n" +
            "    docker run \\\n" +
            "        -u ${HOST_UID} \\\n" +
            "        -v /bamboo-data/${BAMBOO_COMPOSE_PROJECT_NAME}/passwd:/etc/passwd \\\n" +
            "        -v ${BAMBOO_COMPOSE_PROJECT_NAME}_bamboo-data:/srv/bamboo/xml-data/build-dir/ \\\n" +
            "        -e COMPOSER_ROOT_VERSION=${COMPOSER_ROOT_VERSION} \\\n" +
            "        -e HOME=${HOME} \\\n" +
            "        --name ${BAMBOO_COMPOSE_PROJECT_NAME}sib_adhoc \\\n" +
            "        --rm \\\n" +
            "        typo3gmbh/" + requirementIdentifier.toLowerCase() + ":latest \\\n" +
            "        bin/bash -c \"cd ${PWD}; composer $*\"\n" +
            "}\n" +
            "\n";
    }
}
