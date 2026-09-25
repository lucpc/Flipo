CREATE TABLE chaves_api (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL,
    provedor VARCHAR(50) NOT NULL,
    chave_criptografada TEXT NOT NULL,
    CONSTRAINT fk_chaves_api_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuarios (id) ON DELETE CASCADE,
    -- Uma chave por provedor por usuário (docs/02-modelo-de-dados.md).
    CONSTRAINT uk_chaves_api_usuario_provedor UNIQUE (usuario_id, provedor)
);
