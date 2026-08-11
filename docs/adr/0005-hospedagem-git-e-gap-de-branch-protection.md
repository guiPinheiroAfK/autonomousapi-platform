# ADR 0005 — Hospedagem Git inicial e gap de branch protection

- **Status:** Aceito (com gap conhecido)
- **Data:** 2026-08-11
- **Contexto da spec:** `specs/04-repositorio-e-git-workflow.md` (Definition of Done)

## Contexto

A spec 04 exige `main` e `develop` **protegidas** (sem push direto, PR + 1 aprovação +
CI verde) e enforcement de CODEOWNERS. No GitHub, esses recursos em repositório
**privado** só existem em plano pago:
- Conta pessoal: GitHub **Pro** (US$ 4/mês).
- Organização: GitHub **Team** (US$ 4/usuário/mês).

No plano Free (pessoal ou org), branch protection/rulesets só valem em repo **público** —
e o código é proprietário, então público está descartado.

Decisão de negócio no momento: **não** contratar plano pago ainda; a formação do time
(e portanto a criação da org `autonomousapi` + Team) ainda depende de confirmações
pendentes.

## Decisão

1. Hospedar agora em repositório **pessoal privado** `guiPinheiroAfK/autonomousapi-platform`
   no plano Free.
2. Branch default = `develop` (fluxo diário: branches nascem de `develop` e voltam via PR;
   `main` reflete produção).
3. **Branch protection fica como GAP CONHECIDO**, não implementado nesta fase por limitação
   de plano. Enquanto isso, a disciplina de PR/review/CI é **convenção do time**, não
   enforcement técnico.
4. Migração planejada: quando o time for confirmado, criar a org `autonomousapi` (plano
   Team) e **transferir** este repositório para ela (o GitHub preserva histórico, issues,
   PRs e cria redirect da URL antiga). Só então ligar branch protection e enforcement de
   CODEOWNERS.

## Consequências

- ✅ Desbloqueia o trabalho agora sem gasto.
- ✅ CI (GitHub Actions) **funciona** em repo privado Free (2.000 min/mês) — dá para rodar
  lint/testes; o que falta é poder **exigir** o check como gate de merge.
- ⚠️ Até o upgrade, nada impede tecnicamente um push direto em `main`/`develop`. Mitigação:
  todos abrem PR por convenção; ninguém commita direto nas branches longas.
- ⚠️ CODEOWNERS existe como arquivo, mas não é enforced até o plano pago.

## Ação pendente (checklist para o upgrade)

- [ ] Time confirmado → criar org `autonomousapi` (GitHub Team).
- [ ] Transferir `autonomousapi-platform` para a org (renomear para `platform`).
- [ ] Ativar branch protection em `main` e `develop` (no-push-direto, PR + 1 aprovação,
      required status checks = jobs de CI, require review from Code Owners).
- [ ] Substituir placeholders de CODEOWNERS pelos handles reais do time.
- [ ] Atualizar este ADR para "Resolvido".
