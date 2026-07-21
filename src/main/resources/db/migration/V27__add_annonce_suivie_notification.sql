-- V27__add_annonce_suivie_notification.sql
-- Notification « votre annonce est suivie » (APP-119).
--
-- Besoin : l'onglet Alertes du propriétaire restait vide en permanence. Aucun
-- des événements existants ne le concerne (le matching est réservé aux
-- alternants, et les accords formels sont devenus rares depuis la décision
-- « messagerie-first »). Le signal utile pour lui, c'est qu'un étudiant
-- s'intéresse à son annonce.
--
-- Décision produit : on notifie l'INTÉRÊT, jamais le statut. Le statut d'une
-- candidature (« à contacter », « visite prévue », « sans suite »…) est le
-- suivi PRIVÉ de l'étudiant — le propriétaire ne doit jamais le voir.
--
-- ALTER TYPE ... ADD VALUE ne peut pas s'exécuter dans une transaction :
-- c'est possible ici grâce à spring.flyway.execute-in-transaction=false.

ALTER TYPE notification_type ADD VALUE IF NOT EXISTS 'ANNONCE_SUIVIE';
