import type { SVGProps } from "react";

type WordmarkProps = Omit<SVGProps<SVGSVGElement>, "children">;

export function MalaiWordmark(props: WordmarkProps) {
  return (
    <svg
      viewBox="0 0 132 28"
      xmlns="http://www.w3.org/2000/svg"
      data-theme="malai"
      role="img"
      aria-label="malai"
      {...props}
    >
      <g transform="scale(0.875)">
        <path
          fillRule="evenodd"
          fill="currentColor"
          d="M16 3.5 26.5 7.5V15c0 6.8-4.3 11.4-10.5 13.5C9.8 26.4 5.5 21.8 5.5 15V7.5Zm0 7a2.6 2.6 0 0 1 1.1 4.96L18.5 21h-5l1.4-5.54A2.6 2.6 0 0 1 16 10.5Z"
        />
      </g>
      <text
        x="34"
        y="20"
        fontFamily="inherit"
        fontSize="20"
        fontWeight="700"
        letterSpacing="-0.5"
        fill="currentColor"
      >
        malai
      </text>
    </svg>
  );
}

export function HalcyonWordmark(props: WordmarkProps) {
  return (
    <svg
      viewBox="0 0 150 28"
      xmlns="http://www.w3.org/2000/svg"
      data-theme="halcyon"
      role="img"
      aria-label="halcyon"
      {...props}
    >
      <path d="M3 22 L14 6 L25 22 Z" fill="currentColor" stroke="none" />
      <path
        d="M14 6 L25 22 L14 14 Z"
        fill="currentColor"
        opacity="0.55"
        stroke="none"
      />
      <text
        x="33"
        y="20"
        fontFamily="inherit"
        fontSize="20"
        fontWeight="600"
        letterSpacing="0.5"
        fill="currentColor"
      >
        halcyon
      </text>
    </svg>
  );
}

export function LindenWordmark(props: WordmarkProps) {
  return (
    <svg
      viewBox="0 0 140 28"
      xmlns="http://www.w3.org/2000/svg"
      data-theme="linden"
      role="img"
      aria-label="linden"
      {...props}
    >
      <path
        d="M14 3 C 19 8, 23 12, 23 17 A 9 9 0 0 1 5 17 C 5 12, 9 8, 14 3 Z"
        fill="currentColor"
        stroke="none"
      />
      <line
        x1="14"
        y1="11"
        x2="14"
        y2="25"
        stroke="currentColor"
        strokeWidth="1.5"
        opacity="0.45"
      />
      <text
        x="30"
        y="20"
        fontFamily="inherit"
        fontSize="20"
        fontWeight="500"
        letterSpacing="0.2"
        fill="currentColor"
      >
        linden
      </text>
    </svg>
  );
}
