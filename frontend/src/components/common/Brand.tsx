import { Link } from "react-router-dom";
import mainLogo from "../../assets/main-logo.webp";
import mainTitle from "../../assets/main-title.webp";

export default function Brand() {
  return (
    <Link className="inline-flex items-center gap-2" to="/" aria-label="On:마음 홈">
      <img className="h-9 w-9 shrink-0 object-contain" src={mainLogo} alt="" />
      <span className="block h-8 w-20 overflow-hidden" aria-hidden="true">
        <img className="h-8 max-w-none -translate-x-10" src={mainTitle} alt="" />
      </span>
    </Link>
  );
}
