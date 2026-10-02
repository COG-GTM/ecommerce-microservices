import { REGISTER_ID, STORE_ID } from '../api/config';

export interface SessionIdentity {
  associateName: string;
  associateId: string;
  storeId: string;
  registerId: string;
  roles: string[];
}

export const POS_ROLES = ['pos-associate', 'pos-manager'] as const;

export const MOCK_IDENTITY: SessionIdentity = {
  associateName: 'M. Reyes',
  associateId: 'A-4471',
  storeId: STORE_ID,
  registerId: REGISTER_ID,
  roles: ['pos-associate'],
};

export function identityFromToken(parsed: unknown): SessionIdentity {
  const token = typeof parsed === 'object' && parsed !== null
    ? parsed as Record<string, unknown>
    : {};
  const realmAccess = typeof token.realm_access === 'object' && token.realm_access !== null
    ? token.realm_access as Record<string, unknown>
    : {};
  const roles = Array.isArray(realmAccess.roles)
    ? realmAccess.roles.filter((role): role is string => typeof role === 'string')
    : [];

  return {
    associateName: stringClaim(token.name ?? token.preferred_username),
    associateId: stringClaim(token.associate_id),
    storeId: stringClaim(token.store_id),
    registerId: stringClaim(token.register_id),
    roles,
  };
}

export function hasPosRole(identity: SessionIdentity): boolean {
  return identity.roles.some((role) => POS_ROLES.includes(role as (typeof POS_ROLES)[number]));
}

function stringClaim(value: unknown): string {
  return value === undefined || value === null ? '' : String(value);
}
