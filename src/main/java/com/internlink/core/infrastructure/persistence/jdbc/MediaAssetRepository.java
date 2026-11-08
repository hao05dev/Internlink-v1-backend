package com.internlink.core.infrastructure.persistence.jdbc;

import com.internlink.core.application.system.upload.MediaPurpose;
import com.internlink.core.shared.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class MediaAssetRepository {
    private final JdbcTemplate jdbc;
    private final CompanyMediaReferenceRepository companyReferences;

    public record Asset(
            UUID id,
            UUID owner,
            MediaPurpose purpose,
            UUID context,
            String scope,
            String key,
            String name,
            String mime,
            long size,
            String checksum) {
        public String url() {
            return (purpose.privateImage() ? "/api/v1/media/" : "/api/v1/public/media/") + id;
        }
    }

    public void insert(Asset a) {
        jdbc.update(
                "INSERT INTO"
                    + " media_assets(id,owner_user_id,purpose,context_id,scope,provider_file_id,file_name,mime_type,size_bytes,checksum)"
                    + " VALUES(?,?,?,?,?,?,?,?,?,?)",
                a.id(),
                a.owner(),
                a.purpose().name(),
                a.context(),
                a.scope(),
                a.key(),
                a.name(),
                a.mime(),
                a.size(),
                a.checksum());
    }

    public Asset get(UUID id) {
        return jdbc
                .query(
                        "SELECT * FROM media_assets WHERE id=?",
                        (r, i) ->
                                new Asset(
                                        r.getObject("id", UUID.class),
                                        r.getObject("owner_user_id", UUID.class),
                                        MediaPurpose.valueOf(r.getString("purpose")),
                                        r.getObject("context_id", UUID.class),
                                        r.getString("scope"),
                                        r.getString("provider_file_id"),
                                        r.getString("file_name"),
                                        r.getString("mime_type"),
                                        r.getLong("size_bytes"),
                                        r.getString("checksum")),
                        id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("MediaAsset", "id", id));
    }

    public boolean publicReference(Asset a) {
        if (a.purpose().company()) return companyReferences.publicReference(a);
        if (a.purpose().privateImage()) return false;
        String state =
                a.purpose().job()
                        ? "status='APPROVED'"
                        : "status='PUBLISHED' AND visibility='PUBLIC'";
        return referencedWhere(a, state + " AND " + contextColumn(a) + "=?", a.context());
    }

    public boolean departmentReference(Asset a, UUID department) {
        if (a.purpose().company()) return false;
        return referencedWhere(
                a, "department_id=? AND " + contextColumn(a) + "=?", department, a.context());
    }

    /** Ảnh/tệp còn được bất kỳ bài viết/tin nào (mọi trạng thái) tham chiếu hay không. */
    public boolean referenced(Asset a) {
        if (a.purpose().company() || a.purpose().privateImage()) return true;
        return referencedWhere(a, "TRUE");
    }

    public void delete(UUID id) {
        jdbc.update("DELETE FROM media_assets WHERE id=?", id);
    }

    private boolean referencedWhere(Asset a, String where, Object... args) {
        boolean job = a.purpose().job();
        String table = job ? "job_positions" : "career_guide_posts";
        String image = job ? "banner_url" : "cover_image_url";
        String content = job ? "description" : "content";
        String sql =
                "SELECT EXISTS(SELECT 1 FROM "
                        + table
                        + " WHERE "
                        + where
                        + " AND ("
                        + image
                        + "=? OR attachment_urls @> CAST(? AS jsonb)"
                        + " OR gallery_images @> CAST(? AS jsonb) OR position(? in "
                        + content
                        + ")>0))";
        var params = new java.util.ArrayList<Object>(java.util.Arrays.asList(args));
        params.add(a.url());
        params.add("[{\"fileUrl\":\"" + a.url() + "\"}]");
        params.add("[{\"url\":\"" + a.url() + "\"}]");
        params.add(a.url());
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, params.toArray()));
    }

    private static String contextColumn(Asset a) {
        return a.purpose().job() ? "company_id" : "author_user_id";
    }
}
