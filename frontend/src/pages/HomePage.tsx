import Brand from "../components/common/Brand";
import HomeIntro from "../components/home/HomeIntro";
import RoleSelection from "../components/home/RoleSelection";

export default function HomePage() {
  return (
    <main className="grid min-h-screen place-items-center bg-[radial-gradient(circle_at_12%_15%,#f3792926,transparent_30%),radial-gradient(circle_at_90%_86%,#ffb47730,transparent_32%)] p-5">
      <section className="w-full max-w-4xl rounded-3xl border border-stone-200 bg-white/90 p-7 shadow-2xl shadow-stone-300/40 backdrop-blur md:p-12">
        <Brand />
        <HomeIntro />
        <RoleSelection />
      </section>
    </main>
  );
}
