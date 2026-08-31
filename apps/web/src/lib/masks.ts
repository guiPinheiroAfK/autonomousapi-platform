/**
 * Máscaras "formata enquanto digita" (pedido do Guilherme: campo numérico não deveria
 * aceitar qualquer coisa solta — telefone e CNH digitados livres hoje, sem indicar o
 * formato esperado até o usuário errar). Cada função recebe o valor bruto do input (que já
 * pode ter caracteres de máscara de uma digitação anterior) e devolve o texto formatado —
 * sempre reextrai só os dígitos primeiro, então funciona tanto digitando quanto colando.
 */

/** Só dígitos, sem limite — usado como primeiro passo de toda máscara abaixo. */
function apenasDigitos(valor: string): string {
  return valor.replace(/\D/g, '');
}

/**
 * Telefone BR enquanto digita: DDD (2) + número (8 ou 9 dígitos, decide sozinho pelo que
 * já foi digitado — fixo é 8, celular é 9). Cap em 11 dígitos (DDD + 9), o que sobrar do
 * que o usuário digitar/colar é descartado, não empurra o cursor pra formato errado.
 *
 * "(45) 99999-0000" com 9 dígitos locais, "(45) 3333-0000" com 8 — a vírgula entre "9999"
 * e "0000"/"999" muda de posição sozinha conforme o tamanho, não é fixo.
 */
export function maskTelefoneBR(valorBruto: string): string {
  const digitos = apenasDigitos(valorBruto).slice(0, 11);
  if (digitos.length === 0) return '';
  if (digitos.length <= 2) return `(${digitos}`;
  const ddd = digitos.slice(0, 2);
  const resto = digitos.slice(2);
  if (resto.length <= 4) return `(${ddd}) ${resto}`;
  // 8 dígitos locais = fixo (4+4); 9 = celular (5+4). Só decide o corte quando já
  // digitou o suficiente pra saber qual dos dois é.
  const corte = resto.length <= 8 ? 4 : 5;
  return `(${ddd}) ${resto.slice(0, corte)}-${resto.slice(corte)}`;
}

/** CNH: só dígitos, cap em 11 (tamanho fixo do número da CNH no Brasil) — sem separador
 *  visual, não existe um padrão de agrupamento pra CNH como tem CPF/CEP. */
export function maskCnh(valorBruto: string): string {
  return apenasDigitos(valorBruto).slice(0, 11);
}

/**
 * Moeda BR enquanto digita, estilo "caixa eletrônico": os dígitos digitados enchem da
 * direita pra esquerda, os dois últimos sempre são os centavos. Digitar "1", "0", "0"
 * mostra "R$ 0,01" → "R$ 0,10" → "R$ 1,00" — não tem como digitar "R$ 1,0" solto ou
 * esquecer a vírgula, o formato final está sempre certo enquanto digita.
 */
export function maskMoedaBR(valorBruto: string): string {
  const digitos = apenasDigitos(valorBruto).replace(/^0+(?=\d)/, '');
  if (digitos.length === 0) return '';
  const centavos = digitos.padStart(3, '0');
  const inteiro = centavos.slice(0, -2).replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  return `R$ ${inteiro},${centavos.slice(-2)}`;
}

/** Devolve o número por trás de um texto formatado por {@link maskMoedaBR} (ou vazio → 0),
 *  pra mandar pro backend — que espera number, não "R$ 1.234,56". */
export function parseMoedaBR(textoFormatado: string): number {
  const digitos = apenasDigitos(textoFormatado);
  if (digitos.length === 0) return 0;
  return Number(digitos) / 100;
}

/** Inteiro com separador de milhar enquanto digita (odômetro, km) — só dígitos, sem parte
 *  decimal. "20037" vira "20.037" na hora, sem esperar o campo perder foco. */
export function maskInteiroComSeparador(valorBruto: string): string {
  const digitos = apenasDigitos(valorBruto).replace(/^0+(?=\d)/, '');
  if (digitos.length === 0) return '';
  return digitos.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
}

/** Devolve o número por trás de {@link maskInteiroComSeparador} (ou 0 se vazio). */
export function parseInteiroComSeparador(textoFormatado: string): number {
  const digitos = apenasDigitos(textoFormatado);
  return digitos.length === 0 ? 0 : Number(digitos);
}
