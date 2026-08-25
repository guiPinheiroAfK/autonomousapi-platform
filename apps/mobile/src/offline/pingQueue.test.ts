import AsyncStorage from '@react-native-async-storage/async-storage';
import { enqueue, flushBatch, pendingCount, type GpsPing } from './pingQueue';

jest.mock(
  '@react-native-async-storage/async-storage',
  () => require('@react-native-async-storage/async-storage/jest/async-storage-mock'),
);

function ping(recordedAt: string): GpsPing {
  return { recordedAt, lat: -25.5, lon: -54.5 };
}

beforeEach(async () => {
  await AsyncStorage.clear();
});

describe('enqueue / pendingCount', () => {
  it('começa vazia', async () => {
    expect(await pendingCount()).toBe(0);
  });

  it('conta os pings enfileirados', async () => {
    await enqueue(ping('1'));
    await enqueue(ping('2'));
    expect(await pendingCount()).toBe(2);
  });
});

describe('flushBatch', () => {
  it('esvazia a fila inteira quando o servidor aceita tudo', async () => {
    await enqueue(ping('1'));
    await enqueue(ping('2'));
    await enqueue(ping('3'));

    const enviar = jest.fn().mockImplementation(async (lote: GpsPing[]) => lote.length);
    const enviados = await flushBatch(enviar);

    expect(enviados).toBe(3);
    expect(await pendingCount()).toBe(0);
  });

  it('mantém a fila intacta quando o servidor não aceita nada', async () => {
    await enqueue(ping('1'));
    await enqueue(ping('2'));

    const enviar = jest.fn().mockResolvedValue(0);
    const enviados = await flushBatch(enviar);

    expect(enviados).toBe(0);
    expect(await pendingCount()).toBe(2);
  });

  it('descarta só os aceitos e para no aceite parcial, sem tentar de novo', async () => {
    await enqueue(ping('1'));
    await enqueue(ping('2'));
    await enqueue(ping('3'));

    const enviar = jest.fn().mockResolvedValue(1); // servidor só aceita 1 de cada vez
    const enviados = await flushBatch(enviar);

    // Aceite parcial (1 de 3) encerra o loop imediatamente — não insiste no mesmo
    // flushBatch, mesmo que o próximo envio pudesse aceitar mais.
    expect(enviados).toBe(1);
    expect(await pendingCount()).toBe(2);
    expect(enviar).toHaveBeenCalledTimes(1);
  });

  it('preserva a ordem FIFO ao descartar os aceitos', async () => {
    await enqueue(ping('primeiro'));
    await enqueue(ping('segundo'));
    await enqueue(ping('terceiro'));

    const enviar = jest.fn().mockResolvedValue(2);
    await flushBatch(enviar);

    const restante = JSON.parse((await AsyncStorage.getItem('autonomousapi.pingQueue'))!);
    expect(restante).toEqual([ping('terceiro')]);
  });

  it('respeita o tamanho do lote e continua enquanto o servidor aceitar tudo', async () => {
    for (let i = 0; i < 5; i++) await enqueue(ping(String(i)));

    const tamanhosRecebidos: number[] = [];
    const enviar = jest.fn().mockImplementation(async (lote: GpsPing[]) => {
      tamanhosRecebidos.push(lote.length);
      return lote.length;
    });

    const enviados = await flushBatch(enviar, 2);

    expect(enviados).toBe(5);
    expect(tamanhosRecebidos).toEqual([2, 2, 1]); // 5 pings em lotes de 2: 2+2+1
    expect(await pendingCount()).toBe(0);
  });

  it('não perde nem duplica ping enfileirado durante um flush em andamento', async () => {
    // O comentário do módulo chama isso de "bug real, não teórico": o watcher de GPS
    // pode enfileirar no meio de um envio. O lock de exclusividade (comExclusividade)
    // existe pra isso nunca perder (nem duplicar) o ping novo numa corrida
    // leitura-modificação-escrita.
    await enqueue(ping('existente'));

    let liberarPrimeiroEnvio!: () => void;
    const travaDoPrimeiroEnvio = new Promise<void>((resolve) => {
      liberarPrimeiroEnvio = resolve;
    });

    const lotesRecebidos: GpsPing[][] = [];
    const enviar = jest.fn().mockImplementation(async (lote: GpsPing[]) => {
      lotesRecebidos.push(lote);
      if (lotesRecebidos.length === 1) {
        await travaDoPrimeiroEnvio; // só o primeiro lote fica pendente
      }
      return lote.length;
    });

    const flushPromise = flushBatch(enviar);

    // Enfileira um ping novo ENQUANTO o primeiro lote está pendente de resposta —
    // exatamente a corrida que o comentário do código descreve. Esse ping só existe
    // depois que o primeiro lote já tinha sido lido, então não pode estar nele.
    await enqueue(ping('novo-durante-o-flush'));
    liberarPrimeiroEnvio();
    const enviados = await flushPromise;

    // Nada perdido nem duplicado: os dois pings foram enviados, um por lote (o
    // aceite total do primeiro faz o loop continuar e pegar o segundo), e a fila
    // termina vazia.
    expect(enviados).toBe(2);
    expect(lotesRecebidos).toEqual([[ping('existente')], [ping('novo-durante-o-flush')]]);
    expect(await pendingCount()).toBe(0);
  });

  it('não segura o lock de escrita durante a chamada de rede', async () => {
    // Mesma razão do teste acima, do outro lado: enqueue precisa conseguir progredir
    // (não travar esperando o envio) mesmo com um flush em andamento.
    await enqueue(ping('existente'));

    let liberarEnvio!: () => void;
    const travaDoEnvio = new Promise<void>((resolve) => {
      liberarEnvio = resolve;
    });
    const enviar = jest.fn().mockImplementation(async (lote: GpsPing[]) => {
      await travaDoEnvio;
      return lote.length;
    });

    const flushPromise = flushBatch(enviar);
    // Se enqueue estivesse preso atrás do lock que `enviar` segura, isso nunca
    // resolveria antes de liberarEnvio() — a race abaixo prova que não está preso.
    const resultadoDaCorrida = await Promise.race([
      enqueue(ping('novo')).then(() => 'enqueue-progrediu'),
      new Promise((resolve) => setTimeout(() => resolve('travou'), 50)),
    ]);

    expect(resultadoDaCorrida).toBe('enqueue-progrediu');
    liberarEnvio();
    await flushPromise;
  });
});
