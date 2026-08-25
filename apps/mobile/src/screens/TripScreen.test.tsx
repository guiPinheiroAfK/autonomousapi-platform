import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';
import { coreApi, type PageResponse, type TripResponse, type VehicleResponse } from '../api/client';
import { flushBatch, pendingCount } from '../offline/pingQueue';
import { TripScreen } from './TripScreen';

jest.mock('../api/client', () => ({
  coreApi: {
    vehicles: { list: jest.fn() },
    trips: { list: jest.fn(), start: jest.fn(), stop: jest.fn(), submitPingBatch: jest.fn() },
  },
}));

jest.mock('../offline/pingQueue', () => ({
  enqueue: jest.fn(),
  flushBatch: jest.fn(),
  pendingCount: jest.fn(),
}));

jest.mock('expo-location', () => ({
  Accuracy: { Balanced: 3 },
  requestForegroundPermissionsAsync: jest.fn().mockResolvedValue({ status: 'granted' }),
  requestBackgroundPermissionsAsync: jest.fn().mockResolvedValue({ status: 'granted' }),
  watchPositionAsync: jest.fn().mockResolvedValue({ remove: jest.fn() }),
}));

const mockedCoreApi = jest.mocked(coreApi);
const mockedPendingCount = jest.mocked(pendingCount);
const mockedFlushBatch = jest.mocked(flushBatch);

function page<T>(content: T[]): PageResponse<T> {
  return { content, page: 0, size: content.length, totalElements: content.length, totalPages: 1 };
}

const veiculo: VehicleResponse = { id: 'v1', plate: 'ABC1D23', brand: 'Fiat', model: 'Strada', status: 'ATIVO' };

const viagemEmAndamento: TripResponse = {
  id: 't1',
  vehicleId: 'v1',
  status: 'EM_ANDAMENTO',
  startedAt: '2026-08-25T10:00:00Z',
  endedAt: null,
};

beforeEach(() => {
  jest.clearAllMocks();
  mockedPendingCount.mockResolvedValue(0);
});

describe('sem viagem em andamento', () => {
  beforeEach(() => {
    mockedCoreApi.vehicles.list.mockResolvedValue(page([veiculo]));
    mockedCoreApi.trips.list.mockResolvedValue(page([]));
  });

  it('mostra a lista de veículos e mantém "Iniciar viagem" desabilitado até escolher um', async () => {
    render(<TripScreen onLogout={jest.fn()} />);

    await screen.findByText(/ABC1D23/);
    expect(screen.getByText('Iniciar viagem').props.disabled).toBe(true);

    fireEvent.press(screen.getByText(/ABC1D23/));

    await waitFor(() => expect(screen.getByText('Iniciar viagem').props.disabled).toBe(false));
  });

  it('inicia a viagem com o veículo escolhido e passa pra tela de viagem em andamento', async () => {
    mockedCoreApi.trips.start.mockResolvedValue(viagemEmAndamento);

    render(<TripScreen onLogout={jest.fn()} />);
    await screen.findByText(/ABC1D23/);
    fireEvent.press(screen.getByText(/ABC1D23/));
    await waitFor(() => expect(screen.getByText('Iniciar viagem').props.disabled).toBe(false));

    fireEvent.press(screen.getByText('Iniciar viagem'));

    await waitFor(() => expect(mockedCoreApi.trips.start).toHaveBeenCalledWith('v1'));
    await screen.findByText('Finalizar viagem');
  });
});

describe('com viagem já em andamento ao carregar', () => {
  beforeEach(() => {
    mockedCoreApi.vehicles.list.mockResolvedValue(page([veiculo]));
    mockedCoreApi.trips.list.mockResolvedValue(page([viagemEmAndamento]));
    mockedPendingCount.mockResolvedValue(3);
  });

  it('pula direto pra tela de viagem em andamento, sem pedir pra escolher veículo', async () => {
    render(<TripScreen onLogout={jest.fn()} />);

    await screen.findByText('Finalizar viagem');
    expect(screen.queryByText(/ABC1D23/)).toBeNull();
    expect(screen.getByText('Pings na fila: 3')).toBeTruthy();
  });

  it('finalizar viagem chama trips.stop e volta pra tela de escolher veículo', async () => {
    mockedCoreApi.trips.stop.mockResolvedValue({ ...viagemEmAndamento, status: 'FINALIZADA' });

    render(<TripScreen onLogout={jest.fn()} />);
    await screen.findByText('Finalizar viagem');

    fireEvent.press(screen.getByText('Finalizar viagem'));

    await waitFor(() => expect(mockedCoreApi.trips.stop).toHaveBeenCalledWith('t1'));
    await screen.findByText('Selecione o veículo');
  });

  it('sincronizar esvazia a fila em lote e mostra quantos pings foram enviados', async () => {
    mockedFlushBatch.mockImplementation(async (enviar) => {
      // Confirma que TripScreen passa o vehicleId certo pro submitPingBatch por
      // baixo — chama o callback como o pingQueue real chamaria.
      await enviar([{ recordedAt: '2026-08-25T10:00:00Z', lat: 0, lon: 0 }]);
      return 1;
    });
    mockedCoreApi.trips.submitPingBatch.mockResolvedValue({ accepted: 1, received: 1 });
    mockedPendingCount.mockResolvedValueOnce(3).mockResolvedValueOnce(0);

    render(<TripScreen onLogout={jest.fn()} />);
    await screen.findByText('Finalizar viagem');

    fireEvent.press(screen.getByText('Sincronizar'));

    await waitFor(() => expect(mockedCoreApi.trips.submitPingBatch).toHaveBeenCalledWith('t1', expect.any(Array)));
    await screen.findByText(/sincronizado \(1 pings\)/);
  });

  it('sai chama onLogout', async () => {
    const onLogout = jest.fn();
    render(<TripScreen onLogout={onLogout} />);
    await screen.findByText('Finalizar viagem');

    fireEvent.press(screen.getByText('Sair'));

    expect(onLogout).toHaveBeenCalledTimes(1);
  });
});
