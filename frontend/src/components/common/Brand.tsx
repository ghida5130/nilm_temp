import { Link } from 'react-router-dom'
import Icon from './Icon'

export default function Brand() {
  return <Link className="inline-flex items-center gap-2 text-xl font-extrabold text-stone-800" to="/"><span className="grid h-10 w-10 place-items-center rounded-xl bg-brand-500 text-white"><Icon name="shield" /></span>On:마음</Link>
}
