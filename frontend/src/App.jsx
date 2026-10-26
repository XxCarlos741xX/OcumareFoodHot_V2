import { HashRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider, useAuth } from './auth/AuthContext'
import { OperacionProvider } from './auth/OperacionContext'
import { AvisoProvider } from './components/Aviso'
import Shell from './components/Shell'
import Login from './pages/Login'
import Mesas from './pages/Mesas'
import NuevoPedido from './pages/NuevoPedido'
import PedidoDetalle from './pages/PedidoDetalle'
import Pedidos from './pages/Pedidos'
import Salon from './pages/Salon'
import Caja, { CajaTurno } from './pages/Caja'
import CartaGestion from './pages/CartaGestion'
import Gestion from './pages/Gestion'
import Personal from './pages/Personal'
import Ventas from './pages/Ventas'
import Impresoras from './pages/Impresoras'
import Inventario from './pages/Inventario'

function Rutas() {
  const { usuario, esGerencia, esCaja } = useAuth()
  if (!usuario) return <Login />
  return (
    <OperacionProvider key={usuario.id}>
      <Routes>
        <Route element={<Shell />}>
          <Route path="/mesas" element={<Mesas />} />
          <Route path="/pedidos" element={<Pedidos />} />
          <Route path="/pedido/nuevo" element={<NuevoPedido />} />
          <Route path="/pedido/:id" element={<PedidoDetalle />} />
          <Route path="/pedido/:id/agregar" element={<NuevoPedido />} />
          {esCaja && (
            <>
              <Route path="/caja" element={<Caja />} />
              <Route path="/caja/turno/:id" element={<CajaTurno />} />
              <Route path="/inventario" element={<Inventario />} />
            </>
          )}
          {esGerencia && (
            <>
              <Route path="/gestion" element={<Gestion />} />
              <Route path="/impresoras" element={<Impresoras />} />
              <Route path="/ventas" element={<Ventas />} />
              <Route path="/carta" element={<CartaGestion />} />
              <Route path="/salon" element={<Salon />} />
              <Route path="/personal" element={<Personal />} />
            </>
          )}
          <Route path="*" element={<Navigate to="/mesas" replace />} />
        </Route>
      </Routes>
    </OperacionProvider>
  )
}

export default function App() {
  return (
    <HashRouter>
      <AvisoProvider>
        <AuthProvider>
          <Rutas />
        </AuthProvider>
      </AvisoProvider>
    </HashRouter>
  )
}
