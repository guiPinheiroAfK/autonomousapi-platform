import { useState, type FormEvent } from 'react';
import { coreApi } from '../api/client';
import { AuthLayout, BotaoPublico, CampoPublico } from '../components/layout/AuthLayout';

interface Props {
  onVoltarParaLogin: () => void;
  onVoltarParaHome: () => void;
}

export function ForgotPasswordPage({ onVoltarParaLogin, onVoltarParaHome }: Props) {
  const [email, setEmail] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [enviado, setEnviado] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    try {
      await coreApi.auth.forgotPassword({ email });
    } finally {
      // Sempre mostra a mesma confirmação, e-mail existindo ou não (o backend já é
      // silencioso quanto a isso — o front não pode reintroduzir o vazamento aqui).
      setSubmitting(false);
      setEnviado(true);
    }
  }

  if (enviado) {
    return (
      <AuthLayout titulo="Verifique seu e-mail" chamada={<>Quase lá.</>} onVoltar={onVoltarParaHome}>
        <p className="mt-3 text-[14px] leading-relaxed text-[var(--tinta-suave)]">
          Se <span className="text-[var(--tinta)]">{email}</span> tiver uma conta, mandamos um link para redefinir a
          senha. Ele vale por 1 hora.
        </p>
        <p className="mt-8 text-[14px] text-[var(--tinta-suave)]">
          <button type="button" onClick={onVoltarParaLogin} className="link-sublinhado text-[var(--tinta)]">
            Voltar para o login
          </button>
        </p>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout
      titulo="Esqueceu a senha?"
      chamada={<>Sem problema, <em className="italic">manda de novo</em>.</>}
      onVoltar={onVoltarParaHome}
    >
      <p className="mt-3 text-[14px] leading-relaxed text-[var(--tinta-suave)]">
        Informe o e-mail da sua conta e mandamos um link para escolher uma senha nova.
      </p>

      <form onSubmit={handleSubmit} className="mt-8 space-y-4">
        <CampoPublico
          id="email"
          rotulo="E-mail"
          type="email"
          autoComplete="email"
          placeholder="voce@suafrota.com.br"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
        />
        <div className="pt-2">
          <BotaoPublico type="submit" disabled={submitting}>
            {submitting ? 'Enviando...' : 'Enviar link'}
          </BotaoPublico>
        </div>
      </form>

      <p className="mt-8 text-[14px] text-[var(--tinta-suave)]">
        <button type="button" onClick={onVoltarParaLogin} className="link-sublinhado text-[var(--tinta)]">
          Voltar para o login
        </button>
      </p>
    </AuthLayout>
  );
}
