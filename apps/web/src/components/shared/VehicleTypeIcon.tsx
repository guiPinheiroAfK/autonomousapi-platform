import { Bike, Bus, Car, Truck, type LucideIcon } from 'lucide-react';
import { cn } from '../../lib/utils';

const VEHICLE_TYPE_ICON: Record<string, LucideIcon> = {
  CARRO: Car,
  MOTO: Bike,
  VAN: Truck,
  CAMINHAO: Truck,
  ONIBUS: Bus,
};

export const VEHICLE_TYPE_LABEL: Record<string, string> = {
  CARRO: 'Carro',
  MOTO: 'Moto',
  VAN: 'Van',
  CAMINHAO: 'Caminhão',
  ONIBUS: 'Ônibus',
};

export function VehicleTypeIcon({ tipo, className }: { tipo?: string; className?: string }) {
  const Icon = VEHICLE_TYPE_ICON[tipo ?? ''] ?? Car;
  return <Icon className={cn('size-4 text-muted-foreground', className)} />;
}
