-- Additive feedback storage. One review is allowed per completed request.
CREATE TABLE IF NOT EXISTS feedback (
    id BIGINT NOT NULL AUTO_INCREMENT,
    request_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    mechanic_id BIGINT NOT NULL,
    rating INT NOT NULL,
    comment VARCHAR(1000) NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_feedback_request UNIQUE (request_id),
    CONSTRAINT fk_feedback_request FOREIGN KEY (request_id) REFERENCES assistance_requests (id),
    CONSTRAINT fk_feedback_owner FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT fk_feedback_mechanic FOREIGN KEY (mechanic_id) REFERENCES users (id),
    CONSTRAINT chk_feedback_rating CHECK (rating BETWEEN 1 AND 5)
);
