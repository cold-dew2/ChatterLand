"use client";

import Button from "@/shared/components/button/Button";
import Modal from "@/shared/components/modal/Modal";
import { CONSENT_POLICY_VERSION, consentDocuments, type ConsentType } from "@/features/consent/consentPolicy";

export default function ConsentDocumentModal({ type, onClose }: { type: ConsentType; onClose: () => void }) {
  const document = consentDocuments[type];
  return (
    <Modal title={document.title} description={document.summary} onClose={onClose}
      footer={<Button fullWidth onClick={onClose}>확인</Button>}>
      {document.sections.map((section) => (
        <section key={section.heading}>
          <h3 className="mb-1.5 text-sm font-bold text-gray-800">{section.heading}</h3>
          <ul className="list-disc space-y-1 pl-5 text-sm leading-relaxed text-gray-600">
            {section.items.map((item) => <li key={item}>{item}</li>)}
          </ul>
        </section>
      ))}
      <p className="text-xs text-gray-400">안내문 버전 {CONSENT_POLICY_VERSION}</p>
    </Modal>
  );
}
