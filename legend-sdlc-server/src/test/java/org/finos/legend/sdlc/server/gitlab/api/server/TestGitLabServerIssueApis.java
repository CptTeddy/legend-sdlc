// Copyright 2021 Goldman Sachs
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.finos.legend.sdlc.server.gitlab.api.server;

import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.GitLabConfiguration;
import org.finos.legend.sdlc.server.gitlab.api.GitLabIssueApi;
import org.finos.legend.sdlc.server.gitlab.api.GitLabIssueApiTestResource;
import org.finos.legend.sdlc.server.gitlab.api.GitLabProjectApi;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.project.config.ProjectStructureConfiguration;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestGitLabServerIssueApis extends AbstractGitLabServerApiTest
{
    private static GitLabIssueApiTestResource gitLabIssueApiTestResource;

    @BeforeClass
    public static void setup() throws LegendSDLCServerException
    {
        setUpIssueApi();
        cleanUpTestProjects(gitLabIssueApiTestResource.getGitLabProjectApi());
    }

    @AfterClass
    public static void teardown() throws LegendSDLCServerException
    {
        cleanUpTestProjects(gitLabIssueApiTestResource.getGitLabProjectApi());
    }

    @Test
    public void testCreateIssue()
    {
        gitLabIssueApiTestResource.runCreateIssueTest();
    }

    /**
     * Authenticates with OAuth2 and instantiate the test SDLC GitLabIssueApi.
     *
     * @throws LegendSDLCServerException if cannot authenticates to GitLab.
     */
    private static void setUpIssueApi() throws LegendSDLCServerException
    {
        GitLabConfiguration gitLabConfig = GitLabConfiguration.newGitLabConfiguration(null, null, null, null, GitLabConfiguration.NewProjectVisibility.PRIVATE);
        ProjectStructureConfiguration projectStructureConfig = ProjectStructureConfiguration.emptyConfiguration();
        GitLabUserContext gitLabUserContext = prepareGitLabOwnerUserContext();

        GitLabProjectApi gitLabProjectApi = new GitLabProjectApi(gitLabConfig, gitLabUserContext, projectStructureConfig, null, gitLabConfig, backgroundTaskProcessor);
        GitLabIssueApi gitLabIssueApi = new GitLabIssueApi(gitLabUserContext);

        gitLabIssueApiTestResource = new GitLabIssueApiTestResource(gitLabProjectApi, gitLabIssueApi);
    }
}
