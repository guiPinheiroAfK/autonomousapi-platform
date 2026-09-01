import * as SecureStore from 'expo-secure-store';
import { clearTokens, loadAccessToken, loadRefreshToken, saveTokens } from './tokenStorage';

jest.mock('expo-secure-store', () => ({
  setItemAsync: jest.fn(),
  getItemAsync: jest.fn(),
  deleteItemAsync: jest.fn(),
}));

const mockedSecureStore = jest.mocked(SecureStore);

beforeEach(() => {
  jest.clearAllMocks();
});

describe('saveTokens', () => {
  it('salva access e refresh token sob chaves separadas no Keychain/Keystore', async () => {
    await saveTokens('access-123', 'refresh-456');

    expect(mockedSecureStore.setItemAsync).toHaveBeenCalledWith(
      'autonomousapi.accessToken',
      'access-123',
    );
    expect(mockedSecureStore.setItemAsync).toHaveBeenCalledWith(
      'autonomousapi.refreshToken',
      'refresh-456',
    );
  });
});

describe('loadAccessToken', () => {
  it('devolve o token salvo', async () => {
    mockedSecureStore.getItemAsync.mockResolvedValue('access-123');
    expect(await loadAccessToken()).toBe('access-123');
    expect(mockedSecureStore.getItemAsync).toHaveBeenCalledWith('autonomousapi.accessToken');
  });

  it('devolve null quando não há sessão salva', async () => {
    mockedSecureStore.getItemAsync.mockResolvedValue(null);
    expect(await loadAccessToken()).toBeNull();
  });
});

describe('loadRefreshToken', () => {
  it('devolve o refresh token salvo', async () => {
    mockedSecureStore.getItemAsync.mockResolvedValue('refresh-456');
    expect(await loadRefreshToken()).toBe('refresh-456');
    expect(mockedSecureStore.getItemAsync).toHaveBeenCalledWith('autonomousapi.refreshToken');
  });

  it('devolve null quando não há sessão salva', async () => {
    mockedSecureStore.getItemAsync.mockResolvedValue(null);
    expect(await loadRefreshToken()).toBeNull();
  });
});

describe('clearTokens', () => {
  it('apaga as duas chaves', async () => {
    await clearTokens();
    expect(mockedSecureStore.deleteItemAsync).toHaveBeenCalledWith('autonomousapi.accessToken');
    expect(mockedSecureStore.deleteItemAsync).toHaveBeenCalledWith('autonomousapi.refreshToken');
  });
});
