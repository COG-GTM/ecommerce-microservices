const KEYS = ['7', '8', '9', '4', '5', '6', '1', '2', '3', '00', '0', 'C']

export function Keypad({ onKey, id }: { onKey: (key: string) => void; id: string }) {
  return (
    <div className="keypad" data-testid={id}>
      {KEYS.map((key) => (
        <button key={key} type="button" className="key" data-testid={`${id}-${key}`} onClick={() => onKey(key)}>
          {key}
        </button>
      ))}
    </div>
  )
}
