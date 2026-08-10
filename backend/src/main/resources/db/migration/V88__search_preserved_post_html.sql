-- noviis:migration-phase expand
-- Expand opaque HTML preservation markers for keyword search without rewriting stored post bodies.
SET lock_timeout = '5s';

CREATE OR REPLACE FUNCTION noviis_expand_preserved_post_html(input_html TEXT)
RETURNS TEXT
LANGUAGE plpgsql
IMMUTABLE
STRICT
PARALLEL SAFE
AS $$
DECLARE
    expanded_html TEXT := input_html;
    marker_match TEXT[];
    decoded_html TEXT;
BEGIN
    LOOP
        marker_match := regexp_match(
            expanded_html,
            '(<div[^>]*class="[^"]*noviis-sandboxed-post-html[^"]*"[^>]*data-value="([A-Za-z0-9+/=]+)"[^>]*>[[:space:]]*</div>)',
            'i'
        );
        IF marker_match IS NULL THEN
            marker_match := regexp_match(
                expanded_html,
                '(<div[^>]*data-value="([A-Za-z0-9+/=]+)"[^>]*class="[^"]*noviis-sandboxed-post-html[^"]*"[^>]*>[[:space:]]*</div>)',
                'i'
            );
        END IF;
        EXIT WHEN marker_match IS NULL;

        BEGIN
            decoded_html := convert_from(decode(marker_match[2], 'base64'), 'UTF8');
        EXCEPTION WHEN OTHERS THEN
            RETURN expanded_html;
        END;
        expanded_html := replace(expanded_html, marker_match[1], decoded_html);
    END LOOP;
    RETURN expanded_html;
END;
$$;

DROP INDEX CONCURRENTLY IF EXISTS idx_posts_contents_trgm;

-- noviis:online-index idx_posts_expanded_contents_trgm
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_posts_expanded_contents_trgm
    ON posts USING gin (lower(noviis_expand_preserved_post_html(contents)) gin_trgm_ops);

RESET lock_timeout;
