import { Bike, Bus, Car, Truck, type LucideIcon } from 'lucide-react';
import { cn } from '../../lib/utils';

const VEHICLE_TYPE_ICON: Record<string, LucideIcon> = {
  CARRO: Car,
  MOTO: Bike,
  VAN: Truck,
  CAMINHAO: Truck,
  ONIBUS: Bus,
};

export function VehicleTypeIcon({ tipo, className }: { tipo?: string; className?: string }) {
  const Icon = VEHICLE_TYPE_ICON[tipo ?? ''] ?? Car;
  return <Icon className={cn('size-4 text-muted-foreground', className)} />;
}
