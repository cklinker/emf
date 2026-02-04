# GitHub Workflows

## Container Build and Publish

The `build-and-publish-containers.yml` workflow builds and publishes Docker images for the EMF Gateway and Control Plane services to DockerHub.

### Setup Instructions

1. **Create a DockerHub Access Token**
   - Go to [DockerHub Account Settings](https://hub.docker.com/settings/security)
   - Click "New Access Token"
   - Name it (e.g., "github-actions")
   - Copy the token (you won't see it again)

2. **Add the Token to GitHub Secrets**
   - Go to your repository on GitHub
   - Navigate to Settings → Secrets and variables → Actions
   - Click "New repository secret"
   - Name: `DOCKERHUB_TOKEN`
   - Value: Paste your DockerHub access token
   - Click "Add secret"

### Workflow Triggers

The workflow runs automatically when:
- **Push to main branch** - Builds and pushes images with `latest` tag and commit SHA
- **Pull requests** - Builds images only (doesn't push to DockerHub)
- **Manual trigger** - Use the "Run workflow" button in GitHub Actions tab

### Image Tags

Images are tagged with:
- `latest` - Latest build from main branch
- `main-<sha>` - Specific commit SHA from main branch
- `pr-<number>` - Pull request builds (not pushed)

### Published Images

After successful builds, images are available at:
- `cklinker/emf-gateway:latest`
- `cklinker/emf-gateway:main-<sha>`
- `cklinker/emf-control-plane:latest`
- `cklinker/emf-control-plane:main-<sha>`

### Manual Workflow Execution

To manually trigger the workflow:
1. Go to Actions tab in GitHub
2. Select "Build and Publish Containers"
3. Click "Run workflow"
4. Choose branch and whether to push images
5. Click "Run workflow"

### Build Optimization

The workflow uses:
- **Docker Buildx** for advanced build features
- **GitHub Actions cache** for faster subsequent builds
- **Parallel jobs** to build both services simultaneously
- **Path filters** to only build when relevant files change

### Troubleshooting

**Build fails with "unauthorized" error:**
- Verify `DOCKERHUB_TOKEN` secret is set correctly
- Ensure the token has write permissions
- Check that the DockerHub username is correct (`cklinker`)

**Build is slow:**
- First builds take longer (15-20 minutes)
- Subsequent builds use cache and are much faster (5-10 minutes)
- Cache is shared across workflow runs

**Images not appearing on DockerHub:**
- Check that the workflow completed successfully
- Verify you're on the main branch (or manually triggered with push enabled)
- Pull requests don't push images by design
