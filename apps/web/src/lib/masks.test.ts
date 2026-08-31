import { describe, expect, it } from 'vitest';
import {
  maskCnh,
  maskInteiroComSeparador,
  maskMoedaBR,
  maskTelefoneBR,
  parseInteiroComSeparador,
  parseMoedaBR,
} from './masks';

describe('maskTelefoneBR', () => {
  it('formata progressivamente enquanto digita um celular (9 dígitos locais)', () => {
    expect(maskTelefoneBR('4')).toBe('(4');
    expect(maskTelefoneBR('45')).toBe('(45');
    expect(maskTelefoneBR('4599')).toBe('(45) 99');
    expect(maskTelefoneBR('45999990000')).toBe('(45) 99999-0000');
  });

  it('formata um fixo (8 dígitos locais) com corte 4+4', () => {
    expect(maskTelefoneBR('4533330000')).toBe('(45) 3333-0000');
  });

  it('ignora caracteres não numéricos (texto colado já com máscara)', () => {
    expect(maskTelefoneBR('(45) 99999-0000')).toBe('(45) 99999-0000');
  });

  it('corta em 11 dígitos, descarta o resto', () => {
    expect(maskTelefoneBR('459999900001234')).toBe('(45) 99999-0000');
  });

  it('retorna vazio para entrada vazia', () => {
    expect(maskTelefoneBR('')).toBe('');
  });
});

describe('maskCnh', () => {
  it('mantém só dígitos', () => {
    expect(maskCnh('123.456.789-00')).toBe('12345678900');
  });

  it('corta em 11 dígitos', () => {
    expect(maskCnh('123456789001234')).toBe('12345678900');
  });
});

describe('maskMoedaBR', () => {
  it('preenche da direita pra esquerda como caixa eletrônico', () => {
    expect(maskMoedaBR('1')).toBe('R$ 0,01');
    expect(maskMoedaBR('10')).toBe('R$ 0,10');
    expect(maskMoedaBR('100')).toBe('R$ 1,00');
  });

  it('separa milhar quando o valor cresce', () => {
    expect(maskMoedaBR('123456')).toBe('R$ 1.234,56');
  });

  it('retorna vazio para entrada vazia', () => {
    expect(maskMoedaBR('')).toBe('');
  });
});

describe('parseMoedaBR', () => {
  it('devolve o número por trás do texto formatado', () => {
    expect(parseMoedaBR('R$ 1.234,56')).toBe(1234.56);
  });

  it('devolve 0 para texto vazio', () => {
    expect(parseMoedaBR('')).toBe(0);
  });
});

describe('maskInteiroComSeparador', () => {
  it('separa milhar enquanto digita', () => {
    expect(maskInteiroComSeparador('20037')).toBe('20.037');
  });

  it('retorna vazio para entrada vazia', () => {
    expect(maskInteiroComSeparador('')).toBe('');
  });
});

describe('parseInteiroComSeparador', () => {
  it('devolve o número por trás do texto formatado', () => {
    expect(parseInteiroComSeparador('20.037')).toBe(20037);
  });

  it('devolve 0 para texto vazio', () => {
    expect(parseInteiroComSeparador('')).toBe(0);
  });
});
