-- Table ShedLock : verrou distribué pour les jobs planifiés
-- Évite qu'un même job s'exécute deux fois en cas de déploiement multi-instances
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
