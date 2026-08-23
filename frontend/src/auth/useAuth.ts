import { createContext, useContext } from 'react'
export type AuthValue = { username:string; logout:()=>void }
export const AuthContext=createContext<AuthValue|null>(null)
export function useAuth(){const value=useContext(AuthContext);if(!value)throw new Error('Authentication is required');return value}
