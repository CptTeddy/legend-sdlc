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

package org.finos.legend.sdlc.server.gitlab.api;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.finos.legend.sdlc.domain.model.issue.Issue;
import org.finos.legend.sdlc.domain.model.project.Project;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Substantial test resource class for build API tests shared by the docker-based and server-based GitLab tests.
 */
public class GitLabIssueApiTestResource
{
    private final GitLabProjectApi gitLabProjectApi;
    private final GitLabIssueApi gitLabIssueApi;

    public GitLabIssueApiTestResource(GitLabProjectApi gitLabProjectApi, GitLabIssueApi gitLabIssueApi)
    {
        this.gitLabProjectApi = gitLabProjectApi;
        this.gitLabIssueApi = gitLabIssueApi;
    }

    public void runCreateIssueTest()
    {
        String projectName = "IssueTestProject";
        String description = "A test project.";
        ProjectType projectType = ProjectType.PRODUCTION;
        String groupId = "org.finos.sdlc.test";
        String artifactId = "issuetestprojone";
        List<String> tags = Lists.mutable.with("doe", "moffitt");
        String issueTitle = "Initial Good Issue";
        String issueDescription = "Some really good suggestions";

        Project createdProject = gitLabProjectApi.createProject(projectName, description, projectType, groupId, artifactId, tags);

        assertNotNull(createdProject);
        assertEquals(projectName, createdProject.getName());
        assertEquals(description, createdProject.getDescription());
        assertEquals(projectType, createdProject.getProjectType());
        assertEquals(Sets.mutable.withAll(tags), Sets.mutable.withAll(createdProject.getTags()));

        Issue createdIssue = gitLabIssueApi.createIssue(createdProject.getProjectId(), issueTitle, issueDescription);

        assertNotNull(createdIssue);
        assertEquals(issueTitle, createdIssue.getTitle());
        assertEquals(issueDescription, createdIssue.getDescription());
    }

    public GitLabProjectApi getGitLabProjectApi()
    {
        return gitLabProjectApi;
    }
}
