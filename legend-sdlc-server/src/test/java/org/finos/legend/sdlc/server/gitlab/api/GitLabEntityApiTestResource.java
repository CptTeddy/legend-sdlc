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
import org.eclipse.collections.api.factory.Maps;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.project.Project;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.domain.model.review.Review;
import org.finos.legend.sdlc.domain.model.review.ReviewState;
import org.finos.legend.sdlc.server.application.entity.PerformChangesCommand;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceApi;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.GitLabProjectId;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.tools.CallUntil;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.MergeRequestApi;
import org.gitlab4j.api.models.MergeRequest;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Substantial test resource class for Entity API tests shared by the docker-based and server-based GitLab tests.
 */
public class GitLabEntityApiTestResource
{
    private final GitLabProjectApi gitLabProjectApi;
    private final GitLabWorkspaceApi gitLabWorkspaceApi;
    private final GitLabEntityApi gitLabEntityApi;
    private final GitLabReviewApi gitLabCommitterReviewApi;
    private final GitLabReviewApi gitLabApproverReviewApi;
    private final GitLabConflictResolutionApi gitLabConflictResolutionApi;

    private final GitLabUserContext gitLabMemberUserContext;

    private static final Logger LOGGER = LoggerFactory.getLogger(GitLabEntityApiTestResource.class);

    public GitLabEntityApiTestResource(GitLabProjectApi gitLabProjectApi, GitLabWorkspaceApi gitLabWorkspaceApi, GitLabEntityApi gitLabEntityApi, GitLabReviewApi gitLabCommitterReviewApi, GitLabReviewApi gitLabApproverReviewApi, GitLabConflictResolutionApi gitLabConflictResolutionApi, GitLabUserContext gitLabMemberUserContext)
    {
        this.gitLabProjectApi = gitLabProjectApi;
        this.gitLabWorkspaceApi = gitLabWorkspaceApi;
        this.gitLabEntityApi = gitLabEntityApi;
        this.gitLabCommitterReviewApi = gitLabCommitterReviewApi;
        this.gitLabApproverReviewApi = gitLabApproverReviewApi;
        this.gitLabMemberUserContext = gitLabMemberUserContext;
        this.gitLabConflictResolutionApi = gitLabConflictResolutionApi;
    }

    public void runEntitiesInNormalWorkflowTest() throws GitLabApiException
    {
        String projectName = "CommitFlowTestProject";
        String description = "A test project.";
        ProjectType projectType = ProjectType.PRODUCTION;
        String groupId = "org.finos.sdlc.test";
        String artifactId = "entitytestproj";
        List<String> tags = Lists.mutable.with("doe", "moffitt");
        String workspaceName = "entitytestworkspace";

        Project createdProject = gitLabProjectApi.createProject(projectName, description, projectType, groupId, artifactId, tags);

        String projectId = createdProject.getProjectId();
        Workspace createdWorkspace = gitLabWorkspaceApi.newWorkspace(projectId, workspaceName);

        String workspaceId = createdWorkspace.getWorkspaceId();
        List<Entity> initialWorkspaceEntities = gitLabEntityApi.getWorkspaceEntityAccessContext(projectId, workspaceId).getEntities(null, null, null);
        List<Entity> initialProjectEntities = gitLabEntityApi.getProjectEntityAccessContext(projectId).getEntities(null, null, null);

        Assert.assertEquals(Collections.emptyList(), initialWorkspaceEntities);
        Assert.assertEquals(Collections.emptyList(), initialProjectEntities);

        String entityPath = "test::entity";
        String classifierPath = "meta::test::mathematicsDepartment";
        Map<String, String> entityContentMap = Maps.mutable.with(
                "package", "test",
                "name", "entity",
                "math-113", "abstract-algebra",
                "math-185", "complex-analysis");
        gitLabEntityApi.getWorkspaceEntityModificationContext(projectId, workspaceId).createEntity(entityPath, classifierPath, entityContentMap, "initial entity");
        List<Entity> modifiedWorkspaceEntities = gitLabEntityApi.getWorkspaceEntityAccessContext(projectId, workspaceId).getEntities(null, null, null);
        List<Entity> modifiedProjectEntities = gitLabEntityApi.getProjectEntityAccessContext(projectId).getEntities(null, null, null);

        Assert.assertNotNull(modifiedWorkspaceEntities);
        Assert.assertEquals(Collections.emptyList(), modifiedProjectEntities);
        Assert.assertEquals(1, modifiedWorkspaceEntities.size());
        Entity initalEntity = modifiedWorkspaceEntities.get(0);
        Assert.assertEquals(initalEntity.getPath(), entityPath);
        Assert.assertEquals(initalEntity.getClassifierPath(), classifierPath);
        Assert.assertEquals(initalEntity.getContent(), entityContentMap);

        Review testReview = gitLabCommitterReviewApi.createReview(projectId, workspaceId, "Add Courses.", "add two math courses");
        String reviewId = testReview.getId();
        Review approvedReview = gitLabApproverReviewApi.approveReview(projectId, reviewId);

        Assert.assertNotNull(approvedReview);
        Assert.assertEquals(reviewId, approvedReview.getId());
        Assert.assertEquals(ReviewState.OPEN, approvedReview.getState());

        GitLabProjectId sdlcGitLabProjectId = GitLabProjectId.parseProjectId(projectId);
        MergeRequestApi mergeRequestApi = gitLabMemberUserContext.getGitLabAPI(sdlcGitLabProjectId.getGitLabMode()).getMergeRequestApi();
        int parsedMergeRequestId = Integer.parseInt(reviewId);
        int gitlabProjectId = sdlcGitLabProjectId.getGitLabId();

        String requiredStatus = "can_be_merged";
        CallUntil<MergeRequest, GitLabApiException> callUntil = CallUntil.callUntil(
                () -> mergeRequestApi.getMergeRequest(gitlabProjectId, parsedMergeRequestId),
                mr -> requiredStatus.equals(mr.getMergeStatus()),
                10,
                500);
        if (!callUntil.succeeded())
        {
            throw new RuntimeException("Merge request " + approvedReview.getId() + " still does not have status \"" + requiredStatus + "\" after " + callUntil.getTryCount() + " tries");
        }
        LOGGER.info("Waited {} times for merge to have status \"{}\"", callUntil.getTryCount(), requiredStatus);

        gitLabCommitterReviewApi.commitReview(projectId, reviewId, "add two math courses");
        List<Entity> newWorkspaceEntities = gitLabEntityApi.getWorkspaceEntityAccessContext(projectId, workspaceId).getEntities(null, null, null);
        List<Entity> postCommitProjectEntities = gitLabEntityApi.getProjectEntityAccessContext(projectId).getEntities(null, null, null);

        Assert.assertNotNull(postCommitProjectEntities);
        Assert.assertEquals(Collections.emptyList(), newWorkspaceEntities);
        Assert.assertEquals(1, postCommitProjectEntities.size());
        Entity projectEntity = postCommitProjectEntities.get(0);
        Assert.assertEquals(projectEntity.getPath(), entityPath);
        Assert.assertEquals(projectEntity.getClassifierPath(), classifierPath);
        Assert.assertEquals(projectEntity.getContent(), entityContentMap);
    }

    public void runUpdateWorkspaceWithRebaseNoConflict() throws GitLabApiException
    {
        // Create new workspace from previous HEAD
        String projectName = "WorkspaceUpdateTestProject";
        String description = "A test project.";
        ProjectType projectType = ProjectType.PRODUCTION;
        String groupId = "org.finos.sdlc.test";
        String artifactId = "wupdatetestproj";
        List<String> tags = Lists.mutable.with("doe", "moffitt");
        String workspaceName = "workspaceone";

        Project createdProject = gitLabProjectApi.createProject(projectName, description, projectType, groupId, artifactId, tags);

        String projectId = createdProject.getProjectId();
        Workspace createdWorkspace = gitLabWorkspaceApi.newWorkspace(projectId, workspaceName);

        String workspaceId = createdWorkspace.getWorkspaceId();
        List<Entity> initialWorkspaceEntities = gitLabEntityApi.getWorkspaceEntityAccessContext(projectId, workspaceId).getEntities(null, null, null);
        List<Entity> initialProjectEntities = gitLabEntityApi.getProjectEntityAccessContext(projectId).getEntities(null, null, null);

        Assert.assertEquals(Collections.emptyList(), initialWorkspaceEntities);
        Assert.assertEquals(Collections.emptyList(), initialProjectEntities);

        // Create another workspace, commit, review, merge to move project HEAD forward -- use workspace two
        String workspaceTwoName = "workspacetwo";
        Workspace createdWorkspaceTwo = gitLabWorkspaceApi.newWorkspace(projectId, workspaceTwoName);
        String workspaceTwoId = createdWorkspaceTwo.getWorkspaceId();
        List<Entity> initialWorkspaceTwoEntities = gitLabEntityApi.getWorkspaceEntityAccessContext(projectId, workspaceTwoId).getEntities(null, null, null);

        Assert.assertEquals(Collections.emptyList(), initialWorkspaceTwoEntities);

        String entityPath = "test::entity";
        String classifierPath = "meta::test::mathematicsDepartment";
        Map<String, String> entityContentMap = Maps.mutable.with(
                "package", "test",
                "name", "entity",
                "math-113", "abstract-algebra",
                "math-185", "complex-analysis");
        gitLabEntityApi.getWorkspaceEntityModificationContext(projectId, workspaceTwoId).createEntity(entityPath, classifierPath, entityContentMap, "initial entity");
        List<Entity> modifiedWorkspaceEntities = gitLabEntityApi.getWorkspaceEntityAccessContext(projectId, workspaceTwoId).getEntities(null, null, null);
        List<Entity> modifiedProjectEntities = gitLabEntityApi.getProjectEntityAccessContext(projectId).getEntities(null, null, null);

        Assert.assertNotNull(modifiedWorkspaceEntities);
        Assert.assertEquals(Collections.emptyList(), modifiedProjectEntities);
        Assert.assertEquals(1, modifiedWorkspaceEntities.size());
        Entity initalEntity = modifiedWorkspaceEntities.get(0);
        Assert.assertEquals(initalEntity.getPath(), entityPath);
        Assert.assertEquals(initalEntity.getClassifierPath(), classifierPath);
        Assert.assertEquals(initalEntity.getContent(), entityContentMap);

        Review testReview = gitLabCommitterReviewApi.createReview(projectId, workspaceTwoId, "Add Courses.", "add two math courses");
        String reviewId = testReview.getId();
        Review approvedReview = gitLabApproverReviewApi.approveReview(projectId, reviewId);

        Assert.assertNotNull(approvedReview);
        Assert.assertEquals(reviewId, approvedReview.getId());
        Assert.assertEquals(ReviewState.OPEN, approvedReview.getState());

        GitLabProjectId sdlcGitLabProjectId = GitLabProjectId.parseProjectId(projectId);
        MergeRequestApi mergeRequestApi = gitLabMemberUserContext.getGitLabAPI(sdlcGitLabProjectId.getGitLabMode()).getMergeRequestApi();
        Integer parsedMergeRequestId = Integer.parseInt(reviewId);
        Integer gitlabProjectId = sdlcGitLabProjectId.getGitLabId();
        MergeRequest mergeRequest = mergeRequestApi.getMergeRequest(gitlabProjectId, parsedMergeRequestId);

        String requiredStatus = "can_be_merged";
        CallUntil<MergeRequest, GitLabApiException> callUntil = CallUntil.callUntil(
                () -> mergeRequestApi.getMergeRequest(gitlabProjectId, parsedMergeRequestId),
                mr -> requiredStatus.equals(mr.getMergeStatus()),
                10,
                500);
        if (!callUntil.succeeded())
        {
            throw new RuntimeException("Merge request " + approvedReview.getId() + " still does not have status \"" + requiredStatus + "\" after " + callUntil.getTryCount() + " tries");
        }
        LOGGER.info("Waited {} times for merge to have status \"{}\"", callUntil.getTryCount(), requiredStatus);

        gitLabCommitterReviewApi.commitReview(projectId, reviewId, "add two math courses");
        List<Entity> newWorkspaceEntities = gitLabEntityApi.getWorkspaceEntityAccessContext(projectId, workspaceTwoId).getEntities(null, null, null);
        List<Entity> postCommitProjectEntities = gitLabEntityApi.getProjectEntityAccessContext(projectId).getEntities(null, null, null);

        Assert.assertNotNull(postCommitProjectEntities);
        Assert.assertEquals(Collections.emptyList(), newWorkspaceEntities);
        Assert.assertEquals(1, postCommitProjectEntities.size());
        Entity projectEntity = postCommitProjectEntities.get(0);
        Assert.assertEquals(projectEntity.getPath(), entityPath);
        Assert.assertEquals(projectEntity.getClassifierPath(), classifierPath);
        Assert.assertEquals(projectEntity.getContent(), entityContentMap);

        // Create changes and make change in workspace branch -- use workspace
        Map<String, String> currentEntityContentMap = Maps.mutable.with(
                "package", "test",
                "name", "entity",
                "math-113", "abstract-algebra",
                "math-185", "complex-analysis");
        gitLabEntityApi.getWorkspaceEntityModificationContext(projectId, workspaceId).createEntity(entityPath, classifierPath, currentEntityContentMap, "initial entity");
        List<Entity> modifiedWorkspaceEntitiesNew = gitLabEntityApi.getWorkspaceEntityAccessContext(projectId, workspaceId).getEntities(null, null, null);

        Assert.assertNotNull(modifiedWorkspaceEntitiesNew);
        Assert.assertEquals(1, modifiedWorkspaceEntities.size());
        Entity initalEntityNew = modifiedWorkspaceEntitiesNew.get(0);
        Assert.assertEquals(initalEntityNew.getPath(), entityPath);
        Assert.assertEquals(initalEntityNew.getClassifierPath(), classifierPath);
        Assert.assertEquals(initalEntityNew.getContent(), currentEntityContentMap);

        // Update workspace branch and trigger rebase without conflict
        gitLabWorkspaceApi.updateWorkspace(projectId, workspaceId);
        List<Entity> updatedWorkspaceEntities = gitLabEntityApi.getWorkspaceEntityAccessContext(projectId, workspaceId).getEntities(null, null, null);

        Assert.assertNotNull(updatedWorkspaceEntities);
        Assert.assertEquals(1, updatedWorkspaceEntities.size());
        Entity updatedEntity = updatedWorkspaceEntities.get(0);
        Assert.assertEquals(updatedEntity.getPath(), entityPath);
        Assert.assertEquals(updatedEntity.getClassifierPath(), classifierPath);
        Assert.assertEquals(updatedEntity.getContent(), currentEntityContentMap);
    }

    public void runUpdateWorkspaceAndResolveRebaseConflict() throws GitLabApiException
    {
        // Create one workspace to input some changes and commit into project
        String projectName = "WorkspaceUpdateTestProjectTwo";
        String description = "A test project.";
        ProjectType projectType = ProjectType.PRODUCTION;
        String groupId = "org.finos.sdlc.test";
        String artifactId = "wupdatetestprojtwo";
        List<String> tags = Lists.mutable.with("doe", "moffitt");
        String workspaceName = "entitytestworkspace";

        Project createdProject = gitLabProjectApi.createProject(projectName, description, projectType, groupId, artifactId, tags);

        String projectId = createdProject.getProjectId();
        Workspace createdWorkspace = gitLabWorkspaceApi.newWorkspace(projectId, workspaceName);

        String workspaceId = createdWorkspace.getWorkspaceId();
        List<Entity> initialWorkspaceEntities = gitLabEntityApi.getWorkspaceEntityAccessContext(projectId, workspaceId).getEntities(null, null, null);
        List<Entity> initialProjectEntities = gitLabEntityApi.getProjectEntityAccessContext(projectId).getEntities(null, null, null);

        Assert.assertEquals(Collections.emptyList(), initialWorkspaceEntities);
        Assert.assertEquals(Collections.emptyList(), initialProjectEntities);

        String entityPath = "test::entity";
        String classifierPath = "meta::test::mathematicsDepartment";
        Map<String, String> entityContentMap = Maps.mutable.with(
                "package", "test",
                "name", "entity",
                "math-113", "abstract-algebra",
                "math-185", "complex-analysis");
        gitLabEntityApi.getWorkspaceEntityModificationContext(projectId, workspaceId).createEntity(entityPath, classifierPath, entityContentMap, "initial entity");
        List<Entity> modifiedWorkspaceEntities = gitLabEntityApi.getWorkspaceEntityAccessContext(projectId, workspaceId).getEntities(null, null, null);
        List<Entity> modifiedProjectEntities = gitLabEntityApi.getProjectEntityAccessContext(projectId).getEntities(null, null, null);

        Assert.assertNotNull(modifiedWorkspaceEntities);
        Assert.assertEquals(Collections.emptyList(), modifiedProjectEntities);
        Assert.assertEquals(1, modifiedWorkspaceEntities.size());
        Entity initalEntity = modifiedWorkspaceEntities.get(0);
        Assert.assertEquals(initalEntity.getPath(), entityPath);
        Assert.assertEquals(initalEntity.getClassifierPath(), classifierPath);
        Assert.assertEquals(initalEntity.getContent(), entityContentMap);

        Review testReview = gitLabCommitterReviewApi.createReview(projectId, workspaceId, "Add Courses.", "add two math courses");
        String reviewId = testReview.getId();
        Review approvedReview = gitLabApproverReviewApi.approveReview(projectId, reviewId);

        Assert.assertNotNull(approvedReview);
        Assert.assertEquals(reviewId, approvedReview.getId());
        Assert.assertEquals(ReviewState.OPEN, approvedReview.getState());

        GitLabProjectId sdlcGitLabProjectId = GitLabProjectId.parseProjectId(projectId);
        MergeRequestApi mergeRequestApi = gitLabMemberUserContext.getGitLabAPI(sdlcGitLabProjectId.getGitLabMode()).getMergeRequestApi();
        int parsedMergeRequestId = Integer.parseInt(reviewId);
        int gitlabProjectId = sdlcGitLabProjectId.getGitLabId();

        String requiredStatus = "can_be_merged";
        CallUntil<MergeRequest, GitLabApiException> callUntil = CallUntil.callUntil(
                () -> mergeRequestApi.getMergeRequest(gitlabProjectId, parsedMergeRequestId),
                mr -> requiredStatus.equals(mr.getMergeStatus()),
                10,
                500);
        if (!callUntil.succeeded())
        {
            throw new RuntimeException("Merge request " + approvedReview.getId() + " still does not have status \"" + requiredStatus + "\" after " + callUntil.getTryCount() + " tries");
        }
        LOGGER.info("Waited {} times for merge to have status \"{}\"", callUntil.getTryCount(), requiredStatus);

        gitLabCommitterReviewApi.commitReview(projectId, reviewId, "add two math courses");
        List<Entity> newWorkspaceEntities = gitLabEntityApi.getWorkspaceEntityAccessContext(projectId, workspaceId).getEntities(null, null, null);
        List<Entity> postCommitProjectEntities = gitLabEntityApi.getProjectEntityAccessContext(projectId).getEntities(null, null, null);

        Assert.assertNotNull(postCommitProjectEntities);
        Assert.assertEquals(Collections.emptyList(), newWorkspaceEntities);
        Assert.assertEquals(1, postCommitProjectEntities.size());
        Entity projectEntity = postCommitProjectEntities.get(0);
        Assert.assertEquals(projectEntity.getPath(), entityPath);
        Assert.assertEquals(projectEntity.getClassifierPath(), classifierPath);
        Assert.assertEquals(projectEntity.getContent(), entityContentMap);

        // Create two new workspaces
        String workspaceOneName = "workspaceone";
        String workspaceTwoName = "workspacetwo";

        Workspace createdWorkspaceOne = gitLabWorkspaceApi.newWorkspace(projectId, workspaceOneName);
        Workspace createdWorkspaceTwo = gitLabWorkspaceApi.newWorkspace(projectId, workspaceTwoName);

        String workspaceOneId = createdWorkspaceOne.getWorkspaceId();
        String workspaceTwoId = createdWorkspaceTwo.getWorkspaceId();
        List<Entity> initialWorkspaceOneEntities = gitLabEntityApi.getWorkspaceEntityAccessContext(projectId, workspaceOneId).getEntities(null, null, null);
        List<Entity> initialWorkspaceTwoEntities = gitLabEntityApi.getWorkspaceEntityAccessContext(projectId, workspaceTwoId).getEntities(null, null, null);

        Assert.assertEquals(1, initialWorkspaceOneEntities.size());
        Assert.assertEquals(1, initialWorkspaceTwoEntities.size());

        // Update the only entity in workspaceOne, and commit
        Map<String, String> updatedEntityContentMap = Maps.mutable.with(
                "package", "test",
                "name", "entity",
                "math-113", "abstract-algebra-new");
        gitLabEntityApi.getWorkspaceEntityModificationContext(projectId, workspaceOneId).updateEntity(entityPath, classifierPath, updatedEntityContentMap, "update entity");
        List<Entity> workspaceOneEntities = gitLabEntityApi.getWorkspaceEntityAccessContext(projectId, workspaceOneId).getEntities(null, null, null);

        Assert.assertNotNull(workspaceOneEntities);
        Assert.assertEquals(1, workspaceOneEntities.size());
        Entity entity = workspaceOneEntities.get(0);
        Assert.assertEquals(entity.getPath(), entityPath);
        Assert.assertEquals(entity.getClassifierPath(), classifierPath);
        Assert.assertEquals(entity.getContent(), updatedEntityContentMap);

        Review updateReview = gitLabCommitterReviewApi.createReview(projectId, workspaceOneId, "Update Courses.", "update math courses");
        String updateReviewId = updateReview.getId();
        Review approvedUpdateReview = gitLabApproverReviewApi.approveReview(projectId, updateReviewId);

        Assert.assertNotNull(approvedUpdateReview);
        Assert.assertEquals(updateReviewId, approvedUpdateReview.getId());
        Assert.assertEquals(ReviewState.OPEN, approvedUpdateReview.getState());

        int parsedUpdateMergeRequestId = Integer.parseInt(updateReviewId);

        CallUntil<MergeRequest, GitLabApiException> updateCallUntil = CallUntil.callUntil(
                () -> mergeRequestApi.getMergeRequest(gitlabProjectId, parsedUpdateMergeRequestId),
                mr -> requiredStatus.equals(mr.getMergeStatus()),
                10,
                500);
        if (!updateCallUntil.succeeded())
        {
            throw new RuntimeException("Merge request " + approvedUpdateReview.getId() + " still does not have status \"" + requiredStatus + "\" after " + updateCallUntil.getTryCount() + " tries");
        }
        LOGGER.info("Waited {} times for merge to have status \"{}\"", updateCallUntil.getTryCount(), requiredStatus);

        gitLabCommitterReviewApi.commitReview(projectId, updateReviewId, "update math courses");
        List<Entity> postUpdateCommitProjectEntities = gitLabEntityApi.getProjectEntityAccessContext(projectId).getEntities(null, null, null);

        Assert.assertNotNull(postUpdateCommitProjectEntities);
        Assert.assertEquals(1, postUpdateCommitProjectEntities.size());
        Entity updatedProjectEntity = postUpdateCommitProjectEntities.get(0);
        Assert.assertEquals(updatedProjectEntity.getPath(), entityPath);
        Assert.assertEquals(updatedProjectEntity.getClassifierPath(), classifierPath);
        Assert.assertEquals(updatedProjectEntity.getContent(), updatedEntityContentMap);

        // Delete the only entity in workspaceTwo, and update workspace to trigger conflict
        gitLabEntityApi.getWorkspaceEntityModificationContext(projectId, workspaceTwoId).deleteEntity(entityPath, "delete entity");
        List<Entity> workspaceTwoEntities = gitLabEntityApi.getWorkspaceEntityAccessContext(projectId, workspaceTwoId).getEntities(null, null, null);

        Assert.assertEquals(Collections.emptyList(), workspaceTwoEntities);

        CallUntil<Boolean, LegendSDLCServerException> workspaceUpdateCallUntil = CallUntil.callUntil(
                () -> isWorkspaceUpdateCompleted(projectId, workspaceTwoId),
                result -> result,
                10,
                500);
        if (!workspaceUpdateCallUntil.succeeded())
        {
            throw new RuntimeException("Workspace " + workspaceTwoId + " still cannot be updated after " + workspaceUpdateCallUntil.getTryCount() + " tries");
        }
        LOGGER.info("Waited {} times for workspace update to complete.", workspaceUpdateCallUntil.getTryCount());
        Workspace workspaceTwoWithConflict = gitLabWorkspaceApi.getWorkspaceWithConflictResolution(projectId, workspaceTwoId);

        Assert.assertNotNull(workspaceTwoWithConflict);

        // Enter conflict resolution mode, accept with a resolution
        PerformChangesCommand performChangesCommand = new PerformChangesCommand();
        gitLabConflictResolutionApi.acceptConflictResolution(projectId, workspaceTwoId, performChangesCommand);
    }

    public GitLabProjectApi getGitLabProjectApi()
    {
        return gitLabProjectApi;
    }

    private Boolean isWorkspaceUpdateCompleted(String projectId, String workspaceId)
    {
        WorkspaceApi.WorkspaceUpdateReport workspaceUpdateReport;
        try
        {
            workspaceUpdateReport = gitLabWorkspaceApi.updateWorkspace(projectId, workspaceId);
        }
        catch (LegendSDLCServerException exception)
        {
            return false;
        }
        LOGGER.info("workspace update report status: " + workspaceUpdateReport.getStatus().name());
        return true;
    }
}
