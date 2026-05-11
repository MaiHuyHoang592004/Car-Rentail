-- V5__availability_version_bigint.sql
-- Align availability_calendar optimistic-lock version with the JPA Long field.

ALTER TABLE availability_calendar
ALTER COLUMN version TYPE BIGINT;
