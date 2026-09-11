CREATE TABLE cartoes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    materia_id UUID NOT NULL,
    pergunta TEXT NOT NULL,
    resposta TEXT NOT NULL,
    origem VARCHAR(20) NOT NULL,
    arquivado BOOLEAN NOT NULL DEFAULT false,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    ultima_revisao TIMESTAMPTZ,
    CONSTRAINT fk_cartoes_materia FOREIGN KEY (materia_id)
        REFERENCES materias (id) ON DELETE CASCADE,
    -- `origem` usa CHECK em vez de enum nativo do Postgres: evolui sem ALTER TYPE
    -- (ver docs/02-modelo-de-dados.md).
    CONSTRAINT ck_cartoes_origem CHECK (origem IN ('manual', 'ia'))
);

CREATE INDEX idx_cartoes_materia_id ON cartoes (materia_id);
