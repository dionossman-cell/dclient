package com.dclient.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

public class RenderUtil {

    public static void drawBlockBox(PoseStack pose, MultiBufferSource buffers, BlockPos pos, float r, float g, float b, float a) {
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        drawAABBRaw(pose, buffers,
            pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z,
            pos.getX() + 1 - cam.x, pos.getY() + 1 - cam.y, pos.getZ() + 1 - cam.z,
            r, g, b, a);
    }

    public static void drawEntityBox(PoseStack pose, MultiBufferSource buffers, Entity entity, float r, float g, float b, float a) {
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        AABB box = entity.getBoundingBox();
        drawAABBRaw(pose, buffers,
            box.minX - cam.x, box.minY - cam.y, box.minZ - cam.z,
            box.maxX - cam.x, box.maxY - cam.y, box.maxZ - cam.z,
            r, g, b, a);
    }

    /** 3D wireframe box */
    public static void drawAABB(PoseStack pose, MultiBufferSource buffers, AABB box, float r, float g, float b, float a) {
        drawAABBRaw(pose, buffers, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, r, g, b, a);
    }

    /** Inner draw — avoids AABB allocation when coords are already known */
    public static void drawAABBRaw(PoseStack pose, MultiBufferSource buffers,
                                    double minX, double minY, double minZ,
                                    double maxX, double maxY, double maxZ,
                                    float r, float g, float b, float a) {
        VertexConsumer buf = buffers.getBuffer(EspRenderType.getEspLines());
        Matrix4f mat = pose.last().pose();
        float x1 = (float) minX, y1 = (float) minY, z1 = (float) minZ;
        float x2 = (float) maxX, y2 = (float) maxY, z2 = (float) maxZ;
        int ri = (int)(r*255), gi = (int)(g*255), bi = (int)(b*255), ai = (int)(a*255);
        line(buf, mat, x1,y1,z1, x2,y1,z1, ri,gi,bi,ai);
        line(buf, mat, x2,y1,z1, x2,y1,z2, ri,gi,bi,ai);
        line(buf, mat, x2,y1,z2, x1,y1,z2, ri,gi,bi,ai);
        line(buf, mat, x1,y1,z2, x1,y1,z1, ri,gi,bi,ai);
        line(buf, mat, x1,y2,z1, x2,y2,z1, ri,gi,bi,ai);
        line(buf, mat, x2,y2,z1, x2,y2,z2, ri,gi,bi,ai);
        line(buf, mat, x2,y2,z2, x1,y2,z2, ri,gi,bi,ai);
        line(buf, mat, x1,y2,z2, x1,y2,z1, ri,gi,bi,ai);
        line(buf, mat, x1,y1,z1, x1,y2,z1, ri,gi,bi,ai);
        line(buf, mat, x2,y1,z1, x2,y2,z1, ri,gi,bi,ai);
        line(buf, mat, x2,y1,z2, x2,y2,z2, ri,gi,bi,ai);
        line(buf, mat, x1,y1,z2, x1,y2,z2, ri,gi,bi,ai);
    }

    /** 2D flat box at feet level */
    public static void draw2DBox(PoseStack pose, MultiBufferSource buffers, AABB box, float r, float g, float b, float a) {
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        VertexConsumer buf = buffers.getBuffer(EspRenderType.getEspLines());
        Matrix4f mat = pose.last().pose();
        float x1 = (float)(box.minX - cam.x), z1 = (float)(box.minZ - cam.z);
        float x2 = (float)(box.maxX - cam.x), z2 = (float)(box.maxZ - cam.z);
        float y = (float)(box.minY - cam.y);
        int ri = (int)(r*255), gi = (int)(g*255), bi = (int)(b*255), ai = (int)(a*255);
        line(buf, mat, x1,y,z1, x2,y,z1, ri,gi,bi,ai);
        line(buf, mat, x2,y,z1, x2,y,z2, ri,gi,bi,ai);
        line(buf, mat, x2,y,z2, x1,y,z2, ri,gi,bi,ai);
        line(buf, mat, x1,y,z2, x1,y,z1, ri,gi,bi,ai);
    }

    /** Corner-only box (outline style) */
    public static void drawCornerBox(PoseStack pose, MultiBufferSource buffers, AABB box, float r, float g, float b, float a) {
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        VertexConsumer buf = buffers.getBuffer(EspRenderType.getEspLines());
        Matrix4f mat = pose.last().pose();
        float x1 = (float)(box.minX - cam.x), y1 = (float)(box.minY - cam.y), z1 = (float)(box.minZ - cam.z);
        float x2 = (float)(box.maxX - cam.x), y2 = (float)(box.maxY - cam.y), z2 = (float)(box.maxZ - cam.z);
        float lx = (x2-x1) * 0.25f, ly = (y2-y1) * 0.25f, lz = (z2-z1) * 0.25f;
        int ri = (int)(r*255), gi = (int)(g*255), bi = (int)(b*255), ai = (int)(a*255);
        // Bottom corners
        line(buf,mat, x1,y1,z1, x1+lx,y1,z1, ri,gi,bi,ai); line(buf,mat, x1,y1,z1, x1,y1,z1+lz, ri,gi,bi,ai); line(buf,mat, x1,y1,z1, x1,y1+ly,z1, ri,gi,bi,ai);
        line(buf,mat, x2,y1,z1, x2-lx,y1,z1, ri,gi,bi,ai); line(buf,mat, x2,y1,z1, x2,y1,z1+lz, ri,gi,bi,ai); line(buf,mat, x2,y1,z1, x2,y1+ly,z1, ri,gi,bi,ai);
        line(buf,mat, x1,y1,z2, x1+lx,y1,z2, ri,gi,bi,ai); line(buf,mat, x1,y1,z2, x1,y1,z2-lz, ri,gi,bi,ai); line(buf,mat, x1,y1,z2, x1,y1+ly,z2, ri,gi,bi,ai);
        line(buf,mat, x2,y1,z2, x2-lx,y1,z2, ri,gi,bi,ai); line(buf,mat, x2,y1,z2, x2,y1,z2-lz, ri,gi,bi,ai); line(buf,mat, x2,y1,z2, x2,y1+ly,z2, ri,gi,bi,ai);
        // Top corners
        line(buf,mat, x1,y2,z1, x1+lx,y2,z1, ri,gi,bi,ai); line(buf,mat, x1,y2,z1, x1,y2,z1+lz, ri,gi,bi,ai); line(buf,mat, x1,y2,z1, x1,y2-ly,z1, ri,gi,bi,ai);
        line(buf,mat, x2,y2,z1, x2-lx,y2,z1, ri,gi,bi,ai); line(buf,mat, x2,y2,z1, x2,y2,z1+lz, ri,gi,bi,ai); line(buf,mat, x2,y2,z1, x2,y2-ly,z1, ri,gi,bi,ai);
        line(buf,mat, x1,y2,z2, x1+lx,y2,z2, ri,gi,bi,ai); line(buf,mat, x1,y2,z2, x1,y2,z2-lz, ri,gi,bi,ai); line(buf,mat, x1,y2,z2, x1,y2-ly,z2, ri,gi,bi,ai);
        line(buf,mat, x2,y2,z2, x2-lx,y2,z2, ri,gi,bi,ai); line(buf,mat, x2,y2,z2, x2,y2,z2-lz, ri,gi,bi,ai); line(buf,mat, x2,y2,z2, x2,y2-ly,z2, ri,gi,bi,ai);
    }

    /** Skeleton ESP — draws a humanoid bone structure facing the camera */
    public static void drawSkeleton(PoseStack pose, MultiBufferSource buffers, Entity entity, float r, float g, float b, float a) {
        Minecraft mc = Minecraft.getInstance();
        var cam = mc.gameRenderer.getMainCamera().position();
        AABB rawBox = entity.getBoundingBox();
        VertexConsumer buf = buffers.getBuffer(EspRenderType.getEspLines());
        Matrix4f mat = pose.last().pose();
        int ri = (int)(r*255), gi = (int)(g*255), bi = (int)(b*255), ai = (int)(a*255);

        // Center of entity in camera-relative space
        float cx = (float)((rawBox.minX + rawBox.maxX) * 0.5 - cam.x);
        float cz = (float)((rawBox.minZ + rawBox.maxZ) * 0.5 - cam.z);
        float y0 = (float)(rawBox.minY - cam.y);
        float h  = (float)(rawBox.maxY - rawBox.minY);
        float w  = (float)(rawBox.maxX - rawBox.minX);

        // Compute camera-facing right vector (perpendicular to camera->entity, in XZ plane)
        double toCamX = cam.x - entity.getX();
        double toCamZ = cam.z - entity.getZ();
        double len = Math.sqrt(toCamX * toCamX + toCamZ * toCamZ);
        float rx, rz;
        if (len > 0.001) {
            // Right = perpendicular to (toCam) in XZ plane
            rx = (float)(-toCamZ / len);
            rz = (float)( toCamX / len);
        } else {
            rx = 1f; rz = 0f;
        }

        // Joint Y positions
        float yFeet     = y0;
        float yKnee     = y0 + h * 0.26f;
        float yHip      = y0 + h * 0.48f;
        float yShoulder = y0 + h * 0.78f;
        float yNeck     = y0 + h * 0.88f;
        float yHeadBot  = y0 + h * 0.88f;
        float yHeadTop  = y0 + h * 1.0f;

        float hw  = w * 0.48f;  // shoulder half-width
        float lw  = w * 0.22f;  // leg half-spread
        float hhs = w * 0.18f;  // head half-size

        // Helper: offset from center along right vector
        // left = -right, right = +right
        float lShX = cx - hw * rx, lShZ = cz - hw * rz;
        float rShX = cx + hw * rx, rShZ = cz + hw * rz;
        float lHpX = cx - lw * rx, lHpZ = cz - lw * rz;
        float rHpX = cx + lw * rx, rHpZ = cz + lw * rz;

        // Spine
        line(buf, mat, cx, yHip, cz, cx, yNeck, cz, ri, gi, bi, ai);

        // Head box (4 lines forming a square facing camera)
        float hx1 = cx - hhs * rx, hz1 = cz - hhs * rz;
        float hx2 = cx + hhs * rx, hz2 = cz + hhs * rz;
        line(buf, mat, hx1, yHeadBot, hz1, hx2, yHeadBot, hz2, ri, gi, bi, ai);
        line(buf, mat, hx1, yHeadTop, hz1, hx2, yHeadTop, hz2, ri, gi, bi, ai);
        line(buf, mat, hx1, yHeadBot, hz1, hx1, yHeadTop, hz1, ri, gi, bi, ai);
        line(buf, mat, hx2, yHeadBot, hz2, hx2, yHeadTop, hz2, ri, gi, bi, ai);
        // Neck to head
        line(buf, mat, cx, yNeck, cz, cx, yHeadBot, cz, ri, gi, bi, ai);

        // Shoulders bar
        line(buf, mat, lShX, yShoulder, lShZ, rShX, yShoulder, rShZ, ri, gi, bi, ai);
        // Shoulders to spine
        line(buf, mat, cx, yNeck, cz, lShX, yShoulder, lShZ, ri, gi, bi, ai);
        line(buf, mat, cx, yNeck, cz, rShX, yShoulder, rShZ, ri, gi, bi, ai);

        // Left arm: shoulder -> elbow -> wrist (hangs down)
        float lElbY = yShoulder - h * 0.18f;
        float lWrY  = yShoulder - h * 0.34f;
        float lElX  = lShX - hw * 0.08f * rx, lElZ = lShZ - hw * 0.08f * rz;
        line(buf, mat, lShX, yShoulder, lShZ, lElX, lElbY, lElZ, ri, gi, bi, ai);
        line(buf, mat, lElX, lElbY, lElZ, lShX, lWrY, lShZ, ri, gi, bi, ai);

        // Right arm
        float rElX = rShX + hw * 0.08f * rx, rElZ = rShZ + hw * 0.08f * rz;
        line(buf, mat, rShX, yShoulder, rShZ, rElX, lElbY, rElZ, ri, gi, bi, ai);
        line(buf, mat, rElX, lElbY, rElZ, rShX, lWrY, rShZ, ri, gi, bi, ai);

        // Hips bar
        line(buf, mat, lHpX, yHip, lHpZ, rHpX, yHip, rHpZ, ri, gi, bi, ai);

        // Left leg: hip -> knee -> foot
        float lKnX = lHpX - lw * 0.05f * rx, lKnZ = lHpZ - lw * 0.05f * rz;
        line(buf, mat, lHpX, yHip, lHpZ, lKnX, yKnee, lKnZ, ri, gi, bi, ai);
        line(buf, mat, lKnX, yKnee, lKnZ, lHpX, yFeet, lHpZ, ri, gi, bi, ai);

        // Right leg
        float rKnX = rHpX + lw * 0.05f * rx, rKnZ = rHpZ + lw * 0.05f * rz;
        line(buf, mat, rHpX, yHip, rHpZ, rKnX, yKnee, rKnZ, ri, gi, bi, ai);
        line(buf, mat, rKnX, yKnee, rKnZ, rHpX, yFeet, rHpZ, ri, gi, bi, ai);
    }
    public static void drawTracer(PoseStack pose, MultiBufferSource buffers, double tx, double ty, double tz, float r, float g, float b, float a) {
        Minecraft mc = Minecraft.getInstance();
        var cam = mc.gameRenderer.getMainCamera().position();
        var look = mc.player != null ? mc.player.getLookAngle() : net.minecraft.world.phys.Vec3.ZERO;
        float sx = (float)(look.x * 0.1), sy = (float)(look.y * 0.1), sz = (float)(look.z * 0.1);
        float ex = (float)(tx - cam.x), ey = (float)(ty - cam.y), ez = (float)(tz - cam.z);
        VertexConsumer buf = buffers.getBuffer(EspRenderType.getEspLines());
        Matrix4f mat = pose.last().pose();
        int ri = (int)(r*255), gi = (int)(g*255), bi = (int)(b*255), ai = (int)(a*255);
        float nx = ex-sx, ny2 = ey-sy, nz = ez-sz;
        float len = (float) Math.sqrt(nx*nx + ny2*ny2 + nz*nz);
        if (len > 0) { nx /= len; ny2 /= len; nz /= len; }
        buf.addVertex(mat, sx, sy, sz).setColor(ri, gi, bi, ai).setNormal(nx, ny2, nz).setLineWidth(1.0f);
        buf.addVertex(mat, ex, ey, ez).setColor(ri, gi, bi, ai).setNormal(nx, ny2, nz).setLineWidth(1.0f);
    }

    private static void line(VertexConsumer buf, Matrix4f mat,
                              float x1, float y1, float z1, float x2, float y2, float z2,
                              int r, int g, int b, int a) {
        float nx = x2-x1, ny = y2-y1, nz = z2-z1;
        // Normals don't need to be unit-length for line rendering — skip sqrt
        buf.addVertex(mat, x1, y1, z1).setColor(r, g, b, a).setNormal(nx, ny, nz).setLineWidth(1.0f);
        buf.addVertex(mat, x2, y2, z2).setColor(r, g, b, a).setNormal(nx, ny, nz).setLineWidth(1.0f);
    }
}
