ALTER TABLE usuario ADD COLUMN token_valido_desde TIMESTAMP NOT NULL DEFAULT now();
