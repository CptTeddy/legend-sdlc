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
import org.finos.legend.sdlc.server.gitlab.api.GitLabConflictResolutionApi;
import org.finos.legend.sdlc.server.gitlab.api.GitLabEntityApi;
import org.finos.legend.sdlc.server.gitlab.api.GitLabEntityApiTestResource;
import org.finos.legend.sdlc.server.gitlab.api.GitLabProjectApi;
import org.finos.legend.sdlc.server.gitlab.api.GitLabReviewApi;
import org.finos.legend.sdlc.server.gitlab.api.GitLabRevisionApi;
import org.finos.legend.sdlc.server.gitlab.api.GitLabWorkspaceApi;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.project.config.ProjectStructureConfiguration;
import org.gitlab4j.api.GitLabApiException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestGitLabServerEntityApis extends AbstractGitLabServerApiTest
{
    private static GitLabEntityApiTestResource gitLabEntityApiTestResource;

    @BeforeClass
    public static void setup() throws GitLabApiException
    {
        setUpEntityApi();
        cleanUpTestProjects(gitLabEntityApiTestResource.getGitLabProjectApi());
    }

    @AfterClass
    public static void teardown() throws LegendSDLCServerException
    {
        cleanUpTestProjects(gitLabEntityApiTestResource.getGitLabProjectApi());
    }

    @Test
    public void testEntitiesInNormalWorkflow() throws GitLabApiException
    {
        gitLabEntityApiTestResource.runEntitiesInNormalWorkflowTest();
    }

    @Test
    public void testUpdateWorkspaceWithRebaseNoConflictFlow() throws GitLabApiException
    {
        gitLabEntityApiTestResource.runUpdateWorkspaceWithRebaseNoConflictTest();
    }

    @Test
    public void testUpdateWorkspaceAndResolveRebaseConflictFlow() throws GitLabApiException
    {
        gitLabEntityApiTestResource.runUpdateWorkspaceAndResolveRebaseConflict();
    }

    /**
     * Authenticates with OAuth2 and instantiate the test SDLC GitLabEntityApi.
     */
    private static void setUpEntityApi()
    {
        GitLabUserContext gitLabMemberUserContext = prepareGitLabMemberUserContext();
        GitLabUserContext gitLabOwnerUserContext = prepareGitLabOwnerUserContext();
        GitLabConfiguration gitLabConfig = GitLabConfiguration.newGitLabConfiguration(null, null, null, null, GitLabConfiguration.NewProjectVisibility.PRIVATE);
        ProjectStructureConfiguration projectStructureConfig = ProjectStructureConfiguration.emptyConfiguration();

        GitLabProjectApi gitLabProjectApi = new GitLabProjectApi(gitLabConfig, gitLabOwnerUserContext, projectStructureConfig, null, gitLabConfig, backgroundTaskProcessor);
        GitLabRevisionApi gitLabRevisionApi = new GitLabRevisionApi(gitLabMemberUserContext, backgroundTaskProcessor);
        GitLabWorkspaceApi gitLabWorkspaceApi = new GitLabWorkspaceApi(gitLabMemberUserContext, gitLabRevisionApi, backgroundTaskProcessor);
        GitLabEntityApi gitLabEntityApi = new GitLabEntityApi(gitLabMemberUserContext, backgroundTaskProcessor);
        GitLabReviewApi gitLabCommitterReviewApi = new GitLabReviewApi(gitLabMemberUserContext);
        GitLabReviewApi gitLabApproverReviewApi = new GitLabReviewApi(gitLabOwnerUserContext);
        GitLabConflictResolutionApi gitLabConflictResolutionApi = new GitLabConflictResolutionApi(gitLabMemberUserContext, gitLabEntityApi, backgroundTaskProcessor);

        gitLabEntityApiTestResource = new GitLabEntityApiTestResource(gitLabProjectApi, gitLabWorkspaceApi, gitLabEntityApi, gitLabCommitterReviewApi, gitLabApproverReviewApi, gitLabConflictResolutionApi, gitLabMemberUserContext);
    }
}
