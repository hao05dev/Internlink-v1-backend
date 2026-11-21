-- GitHub integration removed: the profile no longer stores a repository link.
ALTER TABLE student_profiles DROP COLUMN IF EXISTS github_url;
