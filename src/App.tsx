import { Navigate, Route, Routes } from 'react-router-dom';
import { ConAcceso, RequiereSesion } from './components/RutasProtegidas';
import Incidentes from './pages/Incidentes';
import Indicadores from './pages/Indicadores';
import Login from './pages/Login';
import MapaEnVivo from './pages/MapaEnVivo';
import PanelGeneral from './pages/PanelGeneral';
import ReportesVecinales from './pages/ReportesVecinales';
import RutasCamiones from './pages/RutasCamiones';

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route element={<RequiereSesion />}>
        <Route index element={<PanelGeneral />} />
        <Route path="mapa" element={<MapaEnVivo />} />
        <Route path="rutas" element={<RutasCamiones />} />
        <Route path="incidentes" element={<Incidentes />} />
        <Route path="reportes" element={<ReportesVecinales />} />
        <Route
          path="indicadores"
          element={
            <ConAcceso ruta="/indicadores">
              <Indicadores />
            </ConAcceso>
          }
        />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
