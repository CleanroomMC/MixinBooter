package org.spongepowered.asm.mixin.struct;

import org.spongepowered.asm.mixin.transformer.throwables.MixinTransformerError;
import org.spongepowered.asm.util.Bytecode;
import org.spongepowered.asm.util.Handles;

public class MemberRef {

    /**
     * A reference to a field or method backed by a method handle
     */
    public static final class Handle extends MemberRef {

        private org.objectweb.asm.Handle handle;

        /**
         * Creates a member reference initially referring to the member referred
         * to by the method handle and the invocation instruction of the method
         * handle.
         *
         * @param handle Initial method handle.
         */
        public Handle(org.objectweb.asm.Handle handle) {
            this.handle = handle;
        }

        /**
         * Gets a method handle for the member this is object is referring to.
         *
         * @return Method handle representing this object
         */
        public org.objectweb.asm.Handle getMethodHandle() {
            return this.handle;
        }

        public boolean isField() {
            return Handles.isField(this.handle);
        }

        public int getOpcode() {
            int opcode = Handles.opcodeFromTag(this.handle.getTag());
            if (opcode == 0) {
                throw new MixinTransformerError("Invalid tag " + this.handle.getTag() + " for method handle " + this.handle + ".");
            }
            return opcode;
        }

        public void setOpcode(int opcode) {
            int tag = Handles.tagFromOpcode(opcode);
            if (tag == 0) {
                throw new MixinTransformerError("Invalid opcode " + Bytecode.getOpcodeName(opcode) + " for method handle " + this.handle + ".");
            }
            this.setHandle(tag, this.handle.getOwner(), this.handle.getName(), this.handle.getDesc());
        }

        public String getOwner() {
            return this.handle.getOwner();
        }

        public void setOwner(String owner) {
            this.setHandle(this.handle.getTag(), owner, this.handle.getName(), this.handle.getDesc());
        }

        public String getName() {
            return this.handle.getName();
        }

        public void setName(String name) {
            this.setHandle(this.handle.getTag(), this.handle.getOwner(), name, this.handle.getDesc());
        }

        public String getDesc() {
            return this.handle.getDesc();
        }

        public void setDesc(String desc) {
            this.setHandle(this.handle.getTag(), this.handle.getOwner(), this.handle.getName(), desc);
        }

        public void setHandle(int tag, String owner, String name, String desc, boolean isInterface) {
            this.handle = new org.objectweb.asm.Handle(tag, owner, name, desc, isInterface);
        }

        public void setHandle(int tag, String owner, String name, String desc) {
            this.handle = new org.objectweb.asm.Handle(tag, owner, name, desc);
        }

    }

}
