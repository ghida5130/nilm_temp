import { Link } from 'react-router-dom'
import Brand from '../components/common/Brand'
import Icon from '../components/common/Icon'

const roles = [
  { to: '/staff', title: '복지담당자', description: '담당 대상자의 상태와 위험 신호를 확인해요.', icon: 'users' as const },
  { to: '/user', title: '복지대상자', description: '외출을 알리고 담당자와 연결해요.', icon: 'home' as const },
]

export default function HomePage() {
  return <main className="grid min-h-screen place-items-center bg-[radial-gradient(circle_at_12%_15%,#d97e3f22,transparent_30%),radial-gradient(circle_at_90%_86%,#edbc842e,transparent_32%)] p-5">
    <section className="w-full max-w-4xl rounded-3xl border border-stone-200 bg-white/90 p-7 shadow-2xl shadow-stone-300/40 backdrop-blur md:p-12">
      <Brand />
      <div className="my-12 md:my-16"><p className="mb-3 text-sm font-extrabold text-brand-700">안심 돌봄 서비스</p><h1 className="text-4xl leading-tight font-extrabold tracking-tight text-stone-800 md:text-5xl">당신의 일상에<br />따뜻한 안심을 더해요.</h1><p className="mt-5 text-stone-500">이용하실 서비스를 선택해 주세요.</p></div>
      <div className="grid gap-4 md:grid-cols-2">{roles.map((role) => <Link className="group flex items-center gap-4 rounded-2xl border border-stone-200 bg-stone-50 p-5 transition hover:-translate-y-0.5 hover:border-brand-400 hover:shadow-lg" to={role.to} key={role.to}><span className="grid h-12 w-12 place-items-center rounded-xl bg-brand-100 text-brand-700"><Icon name={role.icon} /></span><span className="flex-1"><strong className="block text-lg text-stone-800">{role.title}</strong><small className="mt-1 block leading-5 text-stone-500">{role.description}</small></span><Icon className="text-stone-400 transition group-hover:translate-x-1" name="arrow" /></Link>)}</div>
    </section>
  </main>
}
