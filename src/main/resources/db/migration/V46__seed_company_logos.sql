-- ====================================================================
-- INTERNLINK MIGRATION V42: SEED OFFICIAL LOGOS FOR 5 PARTNER COMPANIES
-- Stored on Cloudflare R2 bucket: intern-link-upload
-- ====================================================================


INSERT INTO media_assets (id, owner_user_id, purpose, context_id, scope, provider_file_id, file_name, mime_type, size_bytes, checksum)
VALUES ('a0000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'COMPANY_LOGO', '33333333-3333-3333-3333-333333333333', NULL, 'internlink/logos/fpt_software_logo.png', 'fpt_software_logo.png', 'image/png', 29895, 'bd6a2d3bb3a6fa3e338948e5a24818e264e29f27fadbf4b44ac5da8c514ec250')
ON CONFLICT (provider_file_id) DO UPDATE SET
    file_name = EXCLUDED.file_name,
    size_bytes = EXCLUDED.size_bytes,
    checksum = EXCLUDED.checksum;

UPDATE companies
SET logo_url = '/api/v1/public/media/a0000000-0000-0000-0000-000000000001'
WHERE id = '33333333-3333-3333-3333-333333333333';


INSERT INTO media_assets (id, owner_user_id, purpose, context_id, scope, provider_file_id, file_name, mime_type, size_bytes, checksum)
VALUES ('a0000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', 'COMPANY_LOGO', '44444444-4444-4444-4444-444444444444', NULL, 'internlink/logos/vnpt_logo.png', 'vnpt_logo.png', 'image/png', 63144, '6d7b738679478e10f8d91bb755a4bf6fe1c3dce4fa007dc637e1e1f75053e9b5')
ON CONFLICT (provider_file_id) DO UPDATE SET
    file_name = EXCLUDED.file_name,
    size_bytes = EXCLUDED.size_bytes,
    checksum = EXCLUDED.checksum;

UPDATE companies
SET logo_url = '/api/v1/public/media/a0000000-0000-0000-0000-000000000002'
WHERE id = '44444444-4444-4444-4444-444444444444';


INSERT INTO media_assets (id, owner_user_id, purpose, context_id, scope, provider_file_id, file_name, mime_type, size_bytes, checksum)
VALUES ('a0000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000001', 'COMPANY_LOGO', 'a1b2c3d4-0000-0000-0000-111122223333', NULL, 'internlink/logos/lg_cns_logo.png', 'lg_cns_logo.png', 'image/png', 5201, '3976e9a5ce1b52cd1706c91288265d7aee88d706d09ff336359af9e034e92f40')
ON CONFLICT (provider_file_id) DO UPDATE SET
    file_name = EXCLUDED.file_name,
    size_bytes = EXCLUDED.size_bytes,
    checksum = EXCLUDED.checksum;

UPDATE companies
SET logo_url = '/api/v1/public/media/a0000000-0000-0000-0000-000000000003'
WHERE id = 'a1b2c3d4-0000-0000-0000-111122223333';


INSERT INTO media_assets (id, owner_user_id, purpose, context_id, scope, provider_file_id, file_name, mime_type, size_bytes, checksum)
VALUES ('a0000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000001', 'COMPANY_LOGO', 'b2c3d4e5-0000-0000-0000-222233334444', NULL, 'internlink/logos/viettel_logo.png', 'viettel_logo.png', 'image/png', 17135, '7b699ae548874bb6b3775000aa2f118b70e5320c631a3141266426240ebf774d')
ON CONFLICT (provider_file_id) DO UPDATE SET
    file_name = EXCLUDED.file_name,
    size_bytes = EXCLUDED.size_bytes,
    checksum = EXCLUDED.checksum;

UPDATE companies
SET logo_url = '/api/v1/public/media/a0000000-0000-0000-0000-000000000004'
WHERE id = 'b2c3d4e5-0000-0000-0000-222233334444';


INSERT INTO media_assets (id, owner_user_id, purpose, context_id, scope, provider_file_id, file_name, mime_type, size_bytes, checksum)
VALUES ('a0000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000001', 'COMPANY_LOGO', 'c3d4e5f6-0000-0000-0000-333344445555', NULL, 'internlink/logos/tma_solutions_logo.png', 'tma_solutions_logo.png', 'image/png', 8278, '7f28e9815edfbbc746dc00117b9b142a6c621c15827b0ad52d415db4abcb0a19')
ON CONFLICT (provider_file_id) DO UPDATE SET
    file_name = EXCLUDED.file_name,
    size_bytes = EXCLUDED.size_bytes,
    checksum = EXCLUDED.checksum;

UPDATE companies
SET logo_url = '/api/v1/public/media/a0000000-0000-0000-0000-000000000005'
WHERE id = 'c3d4e5f6-0000-0000-0000-333344445555';
