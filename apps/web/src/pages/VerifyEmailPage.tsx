import { useEffect, useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { AuthLayout, BotaoPublico, ErroPublico } from '../components/layout/AuthLayout';

interface Props {
  token: string;
  onVoltarParaHome: () => void;
  onGoToSignup: () => void;
}

/**
 * Chega aqui pelo link do e-mail (ADR 0011): App.tsx lê `?token=` da URL antes de montar
 * o resto do app e decide essa tela. Confirma sozinha ao montar — não tem formulário,
 * só estado de carregando/erro, porque a única entrada é o token que já veio na URL.
 */
export function VerifyEmailPage({ token, onVoltarParaHome, onGoToSignup }: Props) {
  const { verifyEmail } = useAuth();
  const [status, setStatus] = useState<'confirmando' | 'erro'>('confirmando');
  const [error, setError] = useState('');

  useEffect(() => {
    verifyEmail(token).catch((err) => {
      setError(err instanceof Error ? err.message : 'Não foi possível confirmar o e-mail.');
      setStatus('erro');
    });
  }, [token]);

  if (status === 'confirmando') {
    return (
      <AuthLayout titulo="Confirmando seu e-mail..." chamada={<>Já já você está lá dentro.</>} onVoltar={onVoltarParaHome}>
        <p className="mt-3 text-[14px] text-[var(--tinta-suave)]">Um segundo...</p>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout
      titulo="Não deu certo"
      chamada={<>O link pode ter <em className="italic">expirado</em>.</>}
      onVoltar={onVoltarParaHome}
    >
      <ErroPublico>{error}</ErroPublico>
      <p className="mt-6 text-[14px] text-[var(--tinta-suave)]">
        Links de confirmação valem por 24 horas.{' '}
        <button type="button" onClick={onGoToSignup} className="link-sublinhado text-[var(--tinta)]">
          Cadastre-se de novo
        </button>{' '}
        para receber um novo link, ou peça reenvio na tela de cadastro.
      </p>
      <div className="mt-8">
        <BotaoPublico type="button" onClick={onVoltarParaHome}>
          Voltar para o início
        </BotaoPublico>
      </div>
    </AuthLayout>
  );
}
