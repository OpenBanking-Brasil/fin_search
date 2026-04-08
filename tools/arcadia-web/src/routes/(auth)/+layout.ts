import { redirect } from '@sveltejs/kit';
import type { LayoutLoad } from './$types';

export const load: LayoutLoad = async ({ url }) => {
    // Lista de rotas que requerem autenticação
    const protectedRoutes = ['/dashboard', '/constitution', '/history', '/chat', '/planning', '/reports'];
    
    // Verifica se a rota atual é protegida
    const isProtectedRoute = protectedRoutes.some(route => url.pathname.startsWith(route));
    
    if (isProtectedRoute) {
        // Aqui vamos verificar a autenticação no client-side
        // O redirect será feito via JavaScript no layout component
        return {
            requiresAuth: true,
            currentPath: url.pathname
        };
    }
    
    return {
        requiresAuth: false,
        currentPath: url.pathname
    };
};